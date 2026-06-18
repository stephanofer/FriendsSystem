package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.hera.craftkit.redis.RedisClient;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PresenceService {

    private final ProxyServer server;
    private final RedisClient redis;
    private final PluginConfig config;
    private final Gson gson = new Gson();
    private final Cache<String, Map<UUID, PresenceActivity>> pageCache;

    public PresenceService(ProxyServer server, RedisClient redis, PluginConfig config) {
        this.server = server;
        this.redis = redis;
        this.config = config;
        this.pageCache = Caffeine.newBuilder().expireAfterWrite(config.cache().activityPageTtl()).build();
    }

    public void markOnline(Player player) {
        String serverName = player.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName())
            .orElse("proxy");
        PresenceState state = new PresenceState(
            serverName,
            player.getUsername(),
            Instant.now().toEpochMilli(),
            Instant.now().toEpochMilli()
        );
        this.redis.cache().set(this.presenceKey(player.getUniqueId()), this.gson.toJson(state), this.config.cache().presenceTtl());
    }

    public void markOffline(Player player) {
        this.redis.cache().unlink(this.presenceKey(player.getUniqueId()), this.activityKey(player.getUniqueId()));
    }

    public CompletableFuture<Map<UUID, PresenceActivity>> loadPage(UUID viewer, int page, List<UUID> friendIds) {
        String cacheKey = viewer + ":" + page + ":" + friendIds.hashCode();
        Map<UUID, PresenceActivity> cached = this.pageCache.getIfPresent(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        List<String> keys = new ArrayList<>(friendIds.size() * 2);
        for (UUID friendId : friendIds) {
            keys.add(this.presenceKey(friendId));
            keys.add(this.activityKey(friendId));
        }
        return this.redis.cache().getMany(keys).thenApply(values -> {
            Map<UUID, PresenceActivity> result = new HashMap<>();
            for (UUID friendId : friendIds) {
                PresenceState presence = parse(values.get(this.presenceKey(friendId)), PresenceState.class);
                ActivityState activity = parse(values.get(this.activityKey(friendId)), ActivityState.class);
                result.put(friendId, new PresenceActivity(presence, activity));
            }
            this.pageCache.put(cacheKey, result);
            return result;
        });
    }

    public boolean isOnline(UUID uuid) {
        return this.server.getPlayer(uuid).isPresent();
    }

    private String presenceKey(UUID uuid) {
        return this.redis.key("friends", "presence", uuid.toString());
    }

    private String activityKey(UUID uuid) {
        return this.redis.key("friends", "activity", uuid.toString());
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return this.gson.fromJson(json, type);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record PresenceActivity(PresenceState presence, ActivityState activity) {
    }
}
