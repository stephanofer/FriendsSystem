package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendSuggestionCache {

    private final FriendRepository repository;
    private final Cache<Key, CompletableFuture<List<Profile>>> cache;

    public FriendSuggestionCache(FriendRepository repository, PluginConfig config) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(config.commands().suggestions().cacheTtl())
            .build();
    }

    public CompletableFuture<List<Profile>> incoming(UUID player) {
        return this.cached(player, Type.INCOMING);
    }

    public CompletableFuture<List<Profile>> outgoing(UUID player) {
        return this.cached(player, Type.OUTGOING);
    }

    public CompletableFuture<List<Profile>> blocked(UUID player) {
        return this.cached(player, Type.BLOCKED);
    }

    public void invalidate(UUID player) {
        for (Type type : Type.values()) {
            this.cache.invalidate(new Key(player, type));
        }
    }

    public void invalidate(UUID first, UUID second) {
        this.invalidate(first);
        this.invalidate(second);
    }

    private CompletableFuture<List<Profile>> cached(UUID player, Type type) {
        return this.cache.get(new Key(player, type), key -> switch (key.type()) {
            case INCOMING -> this.repository.incomingRequestProfiles(key.player());
            case OUTGOING -> this.repository.outgoingRequestProfiles(key.player());
            case BLOCKED -> this.repository.blockedPlayers(key.player());
        }).exceptionally(_ -> List.of());
    }

    private enum Type {
        INCOMING,
        OUTGOING,
        BLOCKED
    }

    private record Key(UUID player, Type type) {}
}
