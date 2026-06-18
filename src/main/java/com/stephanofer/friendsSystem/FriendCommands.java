package com.stephanofer.friendsSystem;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public final class FriendCommands {

    private final VelocityCommandManager<CommandSource> manager;
    private final ProxyServer server;
    private final FriendService friends;
    private final SocialMessageService messages;
    private final Messages renderer;
    private final LanguageService languages;
    private final PluginConfig config;
    private final FriendSuggestionCache suggestionCache;

    public FriendCommands(
        VelocityCommandManager<CommandSource> manager,
        ProxyServer server,
        FriendService friends,
        SocialMessageService messages,
        Messages renderer,
        LanguageService languages,
        PluginConfig config,
        FriendSuggestionCache suggestionCache
    ) {
        this.manager = manager;
        this.server = server;
        this.friends = friends;
        this.messages = messages;
        this.renderer = renderer;
        this.languages = languages;
        this.config = config;
        this.suggestionCache = suggestionCache;
    }

    public void register() {
        for (String root : this.config.commands().labels()) {
            this.registerRoot(root);
        }

        for (String alias : this.config.commands().messageAliases()) {
            this.messageAlias(alias);
        }
        for (String alias : this.config.commands().replyAliases()) {
            this.replyAlias(alias);
        }
    }

    private void registerRoot(String root) {
        this.manager.command(this.manager.commandBuilder(root)
            .permission("friendssystem.command.friend")
            .handler(context -> this.help(context.sender(), root)));

        this.manager.command(this.manager.commandBuilder(root)
            .literal("add")
            .required("player", stringParser(), this.onlinePlayerSuggestions())
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.add(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("accept")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.INCOMING))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.accept(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("deny")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.INCOMING))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.deny(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("withdraw")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.OUTGOING))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.withdraw(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("remove")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.FRIENDS))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.remove(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("block")
            .required("player", stringParser(), this.onlinePlayerSuggestions())
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.block(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("unblock")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.BLOCKED))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.unblock(player, context.get("player")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("list")
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.list(player, 1))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("list")
            .required("page", integerParser(1))
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.list(player, context.get("page")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("pending")
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), this.friends::pending)));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("toggle")
            .required("setting", stringParser(), this.settingSuggestions())
            .permission("friendssystem.command.friend")
            .handler(context -> this.player(context.sender(), player -> this.friends.toggle(player, context.get("setting")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("msg")
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.FRIENDS))
            .required("message", greedyStringParser())
            .permission("friendssystem.command.message")
            .handler(context -> this.player(context.sender(), player -> this.messages.message(player, context.get("player"), context.get("message")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("reply")
            .required("message", greedyStringParser())
            .permission("friendssystem.command.message")
            .handler(context -> this.player(context.sender(), player -> this.messages.reply(player, context.get("message")))));
        this.manager.command(this.manager.commandBuilder(root)
            .literal("broadcast")
            .required("message", greedyStringParser())
            .permission("friendssystem.command.broadcast")
            .handler(context -> this.player(context.sender(), player -> this.messages.broadcast(player, context.get("message")))));
    }

    private void messageAlias(String alias) {
        this.manager.command(this.manager.commandBuilder(alias)
            .required("player", stringParser(), this.profileSuggestions(SuggestionType.FRIENDS))
            .required("message", greedyStringParser())
            .permission("friendssystem.command.message")
            .handler(context -> this.player(context.sender(), player -> this.messages.message(player, context.get("player"), context.get("message")))));
    }

    private void replyAlias(String alias) {
        this.manager.command(this.manager.commandBuilder(alias)
            .required("message", greedyStringParser())
            .permission("friendssystem.command.message")
            .handler(context -> this.player(context.sender(), player -> this.messages.reply(player, context.get("message")))));
    }

    private SuggestionProvider<CommandSource> onlinePlayerSuggestions() {
        return (context, input) -> {
            if (!(context.sender() instanceof Player player)) {
                return CompletableFuture.completedFuture(List.of());
            }
            return CompletableFuture.completedFuture(this.toSuggestions(
                this.server.getAllPlayers().stream()
                    .map(online -> new UUIDName(online.getUniqueId(), online.getUsername()))
                    .filter(name -> !name.uuid().equals(player.getUniqueId()))
                    .toList(),
                input
            ));
        };
    }

    private SuggestionProvider<CommandSource> profileSuggestions(SuggestionType type) {
        return (context, input) -> {
            if (!(context.sender() instanceof Player player)) {
                return CompletableFuture.completedFuture(List.of());
            }
            CompletableFuture<List<Profile>> profiles = switch (type) {
                case FRIENDS -> this.friends.friends(player.getUniqueId());
                case INCOMING -> this.suggestionCache.incoming(player.getUniqueId());
                case OUTGOING -> this.suggestionCache.outgoing(player.getUniqueId());
                case BLOCKED -> this.suggestionCache.blocked(player.getUniqueId());
            };
            return profiles.exceptionally(_ -> List.of()).thenApply(list -> this.toSuggestions(list.stream()
                .map(profile -> new UUIDName(profile.uuid(), profile.username()))
                .toList(), input));
        };
    }

    private SuggestionProvider<CommandSource> settingSuggestions() {
        return (context, input) -> CompletableFuture.completedFuture(this.toSuggestions(
            this.config.socialSettingKeys().stream().map(setting -> new UUIDName(null, setting)).toList(),
            input
        ));
    }

    private List<Suggestion> toSuggestions(List<UUIDName> names, CommandInput input) {
        String prefix = input.lastRemainingToken().toLowerCase();
        int limit = prefix.isBlank()
            ? this.config.commands().suggestions().emptyInputMaxResults()
            : this.config.commands().suggestions().filteredMaxResults();
        return names.stream()
            .filter(name -> prefix.isBlank() || name.lowerName().startsWith(prefix))
            .sorted(Comparator.comparing(UUIDName::lowerName))
            .limit(limit)
            .map(name -> Suggestion.suggestion(name.name()))
            .toList();
    }

    private void help(CommandSource source, String root) {
        Map<String, String> placeholders = Map.of("command", root);
        if (source instanceof Player player) {
            this.sendHelp(source, this.languages.language(player), placeholders);
            return;
        }
        this.sendHelp(source, Language.EN, placeholders);
    }

    private void sendHelp(CommandSource source, Language language, Map<String, String> placeholders) {
        source.sendMessage(this.renderer.component(language, "help.header", placeholders));
        for (String key : java.util.List.of(
            "help.add",
            "help.accept",
            "help.deny",
            "help.withdraw",
            "help.remove",
            "help.list",
            "help.pending",
            "help.block",
            "help.unblock",
            "help.msg",
            "help.reply",
            "help.broadcast",
            "help.toggle"
        )) {
            source.sendMessage(this.renderer.component(language, key, placeholders));
        }
    }

    private void player(CommandSource source, PlayerAction action) {
        if (!(source instanceof Player player)) {
            source.sendMessage(this.renderer.component(Language.EN, "player-only", Map.of()));
            return;
        }
        action.execute(player);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void execute(Player player);
    }

    private enum SuggestionType {
        FRIENDS,
        INCOMING,
        OUTGOING,
        BLOCKED
    }

    private record UUIDName(java.util.UUID uuid, String name) {
        private String lowerName() {
            return this.name.toLowerCase();
        }
    }
}
