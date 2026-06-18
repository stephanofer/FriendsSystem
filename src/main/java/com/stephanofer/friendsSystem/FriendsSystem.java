package com.stephanofer.friendsSystem;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.DatabaseConfig;
import com.hera.craftkit.database.Databases;
import com.hera.craftkit.database.MigrationConfig;
import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisConfig;
import com.hera.craftkit.redis.RedisClients;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.velocity.CloudInjectionModule;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.slf4j.Logger;

@Plugin(
    id = "friendssystem",
    name = "FriendsSystem",
    version = "1.0",
    authors = {"Stephanofer"}
)
public final class FriendsSystem {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Injector injector;

    private PluginConfig config;
    private Messages messages;
    private Database database;
    private RedisClient redis;
    private FriendRepository repository;
    private LuckPermsGateway luckPerms;
    private LanguageService languages;
    private PresenceService presence;
    private FriendService friends;
    private SocialMessageService socialMessages;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    @Inject
    public FriendsSystem(
        ProxyServer server,
        Logger logger,
        @DataDirectory Path dataDirectory,
        Injector injector
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.injector = injector;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            this.config = PluginConfig.load(this.dataDirectory);
            this.messages = Messages.load(this.dataDirectory);
            this.database = Databases.mysql(DatabaseConfig.builder()
                .host(this.config.database().host())
                .port(this.config.database().port())
                .database(this.config.database().database())
                .username(this.config.database().username())
                .password(this.config.database().password())
                .tablePrefix(this.config.database().tablePrefix())
                .migration(MigrationConfig.builder()
                    .existingSchemaStrategy(com.hera.craftkit.database.ExistingSchemaStrategy.BASELINE_AT_ZERO)
                    .classLoader(this.getClass().getClassLoader())
                    .build())
                .build());
            this.database.migrate().join();
            this.redis = RedisClients.lettuce(RedisConfig.builder()
                .host(this.config.redis().host())
                .port(this.config.redis().port())
                .database(this.config.redis().database())
                .username(this.config.redis().username())
                .password(this.config.redis().password())
                .ssl(this.config.redis().ssl())
                .keyPrefix(this.config.redis().keyPrefix())
                .environment(this.config.redis().environment())
                .serverId(this.config.redis().serverId())
                .build());
            this.redis.ping().join();

            this.repository = new FriendRepository(this.database, this.config);
            this.luckPerms = new LuckPermsGateway(this.logger, this.config);
            this.languages = new LanguageService(this.database, this.config);
            this.presence = new PresenceService(this.server, this.redis, this.config);
            this.friends = new FriendService(
                this.server,
                this.repository,
                this.presence,
                this.luckPerms,
                this.languages,
                this.messages,
                this.config
            );
            this.socialMessages = new SocialMessageService(
                this.server,
                this.repository,
                this.presence,
                this.languages,
                this.messages,
                this.config
            );

            this.registerCommands();
            this.registerTasks();
            this.logger.info("FriendsSystem enabled.");
        } catch (Exception exception) {
            this.closeQuietly();
            throw new IllegalStateException("Unable to enable FriendsSystem", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        this.closeQuietly();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        var player = event.getPlayer();
        this.languages.loadOnJoin(player);
        this.luckPerms.snapshot(player).thenAccept(snapshot -> this.repository.upsertProfile(
            player.getUniqueId(),
            player.getUsername(),
            snapshot.prefix(),
            snapshot.primaryGroup()
        ));
        this.presence.markOnline(player);
        this.socialMessages.deliverOfflineMessages(player);
        this.friends.notifyFriendsConnection(player, true);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        var player = event.getPlayer();
        this.presence.markOffline(player);
        this.repository.markLastSeen(player.getUniqueId());
        this.languages.evict(player.getUniqueId());
        this.friends.notifyFriendsConnection(player, false);
    }

    private void registerCommands() {
        Injector childInjector = this.injector.createChildInjector(new CloudInjectionModule<>(
            CommandSource.class,
            ExecutionCoordinator.simpleCoordinator(),
            SenderMapper.identity()
        ));
        VelocityCommandManager<CommandSource> manager = childInjector.getInstance(
            Key.get(new TypeLiteral<VelocityCommandManager<CommandSource>>() {})
        );
        MinecraftExceptionHandler.<CommandSource>createNative()
            .defaultHandlers()
            .registerTo(manager);
        new FriendCommands(manager, this.server, this.friends, this.socialMessages, this.messages, this.languages, this.config)
            .register();
    }

    private void registerTasks() {
        this.tasks.add(this.server.getScheduler()
            .buildTask(this, () -> this.server.getAllPlayers().forEach(this.presence::markOnline))
            .repeat(30, TimeUnit.SECONDS)
            .schedule());
        this.tasks.add(this.server.getScheduler()
            .buildTask(this, () -> this.repository.deleteExpiredRequests())
            .delay(1, TimeUnit.MINUTES)
            .repeat(15, TimeUnit.MINUTES)
            .schedule());
        this.tasks.add(this.server.getScheduler()
            .buildTask(this, () -> this.repository.deleteExpiredOfflineMessages())
            .delay(2, TimeUnit.MINUTES)
            .repeat(Duration.ofHours(1))
            .schedule());
    }

    private void closeQuietly() {
        for (ScheduledTask task : this.tasks) {
            task.cancel();
        }
        this.tasks.clear();
        if (this.redis != null) {
            this.redis.close();
        }
        if (this.database != null) {
            this.database.close();
        }
    }
}
