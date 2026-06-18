package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SocialMessageService {

    private final ProxyServer server;
    private final FriendRepository repository;
    private final PresenceService presence;
    private final LanguageService languages;
    private final Messages messages;
    private final PluginConfig config;
    private final Cache<UUID, UUID> lastMessage = Caffeine.newBuilder().expireAfterWrite(java.time.Duration.ofMinutes(10)).build();

    public SocialMessageService(
        ProxyServer server,
        FriendRepository repository,
        PresenceService presence,
        LanguageService languages,
        Messages messages,
        PluginConfig config
    ) {
        this.server = server;
        this.repository = repository;
        this.presence = presence;
        this.languages = languages;
        this.messages = messages;
        this.config = config;
    }

    public void message(Player sender, String targetName, String message) {
        Language language = this.languages.language(sender);
        String normalized = normalizeMessage(message);
        if (normalized.isBlank()) {
            send(sender, language, "message.empty");
            return;
        }
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.areFriends(sender.getUniqueId(), target.uuid()).thenCompose(areFriends -> {
                if (!areFriends) {
                    send(sender, language, "friend.not-friends", "player", target.username());
                    return done();
                }
                return this.repository.settings(target.uuid()).thenCompose(settings -> {
                    if (!settings.allowFriendMessages() || settings.mutedAllFriends()) {
                        send(sender, language, "message.disabled", "player", target.username());
                        return done();
                    }
                    Optional<Player> onlineTarget = this.server.getPlayer(target.uuid());
                    if (onlineTarget.isPresent()) {
                        deliver(sender, onlineTarget.get(), normalized);
                        return done();
                    }
                    return this.repository.storeOfflineMessage(sender.getUniqueId(), target.uuid(), normalized).thenAccept(_ ->
                        send(sender, language, "message.offline-stored", "player", target.username())
                    );
                });
            });
        });
    }

    public void reply(Player sender, String message) {
        UUID target = this.lastMessage.getIfPresent(sender.getUniqueId());
        if (target == null) {
            send(sender, this.languages.language(sender), "message.no-reply");
            return;
        }
        this.server.getPlayer(target).ifPresentOrElse(
            player -> this.message(sender, player.getUsername(), message),
            () -> send(sender, this.languages.language(sender), "message.reply-offline")
        );
    }

    public void broadcast(Player sender, String message) {
        Language language = this.languages.language(sender);
        String normalized = normalizeMessage(message);
        if (normalized.isBlank()) {
            send(sender, language, "message.empty");
            return;
        }
        this.repository.friends(sender.getUniqueId()).thenCompose(friends -> {
            List<CompletableFuture<Boolean>> deliveries = new java.util.ArrayList<>();
            for (Profile friend : friends) {
                Optional<Player> target = this.server.getPlayer(friend.uuid());
                if (target.isEmpty()) {
                    continue;
                }
                Player targetPlayer = target.get();
                deliveries.add(this.repository.settings(targetPlayer.getUniqueId()).thenApply(settings -> {
                    if (settings.allowFriendBroadcasts() && !settings.mutedAllFriends()) {
                        targetPlayer.sendMessage(this.messages.component(this.languages.language(targetPlayer), "message.broadcast-received", Map.of(
                            "player", sender.getUsername(),
                            "message", normalized
                        )));
                        return true;
                    }
                    return false;
                }));
            }
            return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new)).thenAccept(_ -> {
                long delivered = deliveries.stream().filter(future -> future.join()).count();
                send(sender, language, "message.broadcast-sent", "count", String.valueOf(delivered));
            });
        });
    }

    public void deliverOfflineMessages(Player player) {
        this.repository.claimOfflineMessages(player.getUniqueId()).thenAccept(messages -> {
            if (messages.isEmpty()) {
                return;
            }
            Language language = this.languages.language(player);
            player.sendMessage(this.messages.component(language, "offline.header", Map.of("count", String.valueOf(messages.size()))));
            for (FriendRepository.OfflineMessage message : messages) {
                this.repository.friends(player.getUniqueId()).thenAccept(_ -> player.sendMessage(this.messages.component(language, "offline.entry", Map.of(
                    "message", message.message(),
                    "sender", message.senderUuid().toString()
                ))));
            }
            this.repository.markOfflineMessagesDelivered(messages.stream().map(FriendRepository.OfflineMessage::id).toList());
        });
    }

    private void deliver(Player sender, Player target, String message) {
        this.lastMessage.put(sender.getUniqueId(), target.getUniqueId());
        this.lastMessage.put(target.getUniqueId(), sender.getUniqueId());
        target.sendMessage(this.messages.component(this.languages.language(target), "message.received", Map.of(
            "player", sender.getUsername(),
            "message", message
        )));
        sender.sendMessage(this.messages.component(this.languages.language(sender), "message.sent", Map.of(
            "player", target.getUsername(),
            "message", message
        )));
    }

    private CompletableFuture<Optional<Profile>> resolveTarget(String name) {
        Optional<Player> online = this.server.getPlayer(name);
        if (online.isPresent()) {
            Player player = online.get();
            return CompletableFuture.completedFuture(Optional.of(new Profile(
                player.getUniqueId(), player.getUsername(), player.getUsername().toLowerCase(), "", "", null
            )));
        }
        return this.repository.findProfileByName(name);
    }

    private String normalizeMessage(String message) {
        String normalized = message.strip();
        if (normalized.length() > this.config.friends().messageMaxLength()) {
            return normalized.substring(0, this.config.friends().messageMaxLength());
        }
        return Messages.escape(normalized);
    }

    private void send(Player player, Language language, String key) {
        this.messages.send(player, language, key, Map.of());
    }

    private void send(Player player, Language language, String key, String placeholder, String value) {
        this.messages.send(player, language, key, Map.of(placeholder, value));
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
