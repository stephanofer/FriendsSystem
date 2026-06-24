package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.UUID;

public final class CommandCooldowns {

    private final PluginConfig.Cooldowns config;
    private final Cache<Key, Long> executions;

    public CommandCooldowns(PluginConfig.Cooldowns config) {
        this.config = config;
        Duration max = config.max();
        if (max.isZero() || max.isNegative()) {
            max = Duration.ofMillis(1);
        }
        this.executions = Caffeine.newBuilder().expireAfterWrite(max).build();
    }

    public Duration list(UUID player) {
        return check(player, "list", this.config.list());
    }

    public Duration message(UUID player) {
        return check(player, "message", this.config.message());
    }

    public Duration reply(UUID player) {
        return check(player, "reply", this.config.reply());
    }

    public Duration broadcast(UUID player) {
        return check(player, "broadcast", this.config.broadcast());
    }

    public Duration toggle(UUID player) {
        return check(player, "toggle", this.config.toggle());
    }

    public Duration pending(UUID player) {
        return check(player, "pending", this.config.pending());
    }

    public Duration inbox(UUID player) {
        return check(player, "inbox", this.config.inbox());
    }

    private Duration check(UUID player, String action, Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) {
            return Duration.ZERO;
        }
        Key key = new Key(player, action);
        long now = System.currentTimeMillis();
        Long last = this.executions.getIfPresent(key);
        if (last != null) {
            long remaining = cooldown.toMillis() - (now - last);
            if (remaining > 0) {
                return Duration.ofMillis(remaining);
            }
        }
        this.executions.put(key, now);
        return Duration.ZERO;
    }

    private record Key(UUID player, String action) {
    }
}
