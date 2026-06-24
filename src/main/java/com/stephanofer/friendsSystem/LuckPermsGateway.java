package com.stephanofer.friendsSystem;

import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;

public final class LuckPermsGateway {

    private final LuckPerms luckPerms;
    private final Logger logger;
    private final PluginConfig config;

    public LuckPermsGateway(Logger logger, PluginConfig config) {
        this.logger = logger;
        this.config = config;
        LuckPerms resolved = null;
        try {
            resolved = LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            this.logger.warn("LuckPerms is not available. Friend limits and prefixes will use defaults.");
        }
        this.luckPerms = resolved;
    }

    public CompletableFuture<LuckPermsSnapshot> snapshot(Player player) {
        if (this.luckPerms == null) {
            return CompletableFuture.completedFuture(LuckPermsSnapshot.empty());
        }
        User user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return this.loadOfflineSnapshot(player.getUniqueId());
        }
        return CompletableFuture.completedFuture(snapshot(user));
    }

    public int friendLimit(Player player) {
        return playerLimit(player, this.config.limits().friends());
    }

    public int offlineMessageLimit(Player player) {
        return playerLimit(player, this.config.limits().offlineMessages());
    }

    public CompletableFuture<Integer> friendLimit(UUID uuid) {
        return userLimit(uuid, this.config.limits().friends());
    }

    public CompletableFuture<Integer> offlineMessageLimit(UUID uuid) {
        return userLimit(uuid, this.config.limits().offlineMessages());
    }

    public CompletableFuture<LuckPermsSnapshot> loadOfflineSnapshot(UUID uuid) {
        if (this.luckPerms == null) {
            return CompletableFuture.completedFuture(LuckPermsSnapshot.empty());
        }
        return this.luckPerms.getUserManager().loadUser(uuid).thenApply(LuckPermsGateway::snapshot).exceptionally(throwable -> {
            this.logger.warn("Unable to resolve LuckPerms snapshot for {}. Using empty prefix.", uuid, throwable);
            return LuckPermsSnapshot.empty();
        });
    }

    private static LuckPermsSnapshot snapshot(User user) {
        var meta = user.getCachedData().getMetaData();
        return new LuckPermsSnapshot(nullToEmpty(meta.getPrefix()), nullToEmpty(user.getPrimaryGroup()));
    }

    private int playerLimit(Player player, PluginConfig.LimitGroup group) {
        int best = group.defaultLimit();
        for (PluginConfig.PermissionLimit permission : group.permissions()) {
            if (permission.limit() > best && player.hasPermission(permission.permission())) {
                best = permission.limit();
            }
        }
        return best;
    }

    private CompletableFuture<Integer> userLimit(UUID uuid, PluginConfig.LimitGroup group) {
        if (this.luckPerms == null) {
            return CompletableFuture.completedFuture(group.defaultLimit());
        }
        return this.luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
            int best = group.defaultLimit();
            var permissions = user.getCachedData().getPermissionData(user.getQueryOptions());
            for (PluginConfig.PermissionLimit permission : group.permissions()) {
                if (permission.limit() > best && permissions.checkPermission(permission.permission()).asBoolean()) {
                    best = permission.limit();
                }
            }
            return best;
        }).exceptionally(throwable -> {
            this.logger.warn("Unable to resolve LuckPerms limits for {}. Using default limit.", uuid, throwable);
            return group.defaultLimit();
        });
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
