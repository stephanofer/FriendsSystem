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
            return CompletableFuture.completedFuture(LuckPermsSnapshot.empty());
        }
        return CompletableFuture.completedFuture(snapshot(user));
    }

    public int friendLimit(Player player) {
        int best = this.config.permissions().defaultLimit();
        int max = Math.max(best, this.config.permissions().maxLimitScan());
        String prefix = this.config.permissions().limitPrefix();
        for (int limit = best + 1; limit <= max; limit++) {
            if (player.hasPermission(prefix + limit)) {
                best = limit;
            }
        }
        return best;
    }

    public CompletableFuture<LuckPermsSnapshot> loadOfflineSnapshot(UUID uuid) {
        if (this.luckPerms == null) {
            return CompletableFuture.completedFuture(LuckPermsSnapshot.empty());
        }
        return this.luckPerms.getUserManager().loadUser(uuid).thenApply(LuckPermsGateway::snapshot);
    }

    private static LuckPermsSnapshot snapshot(User user) {
        var meta = user.getCachedData().getMetaData();
        return new LuckPermsSnapshot(nullToEmpty(meta.getPrefix()), nullToEmpty(user.getPrimaryGroup()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
