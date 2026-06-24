package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class SocialMessageService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final ProxyServer server;
    private final FriendRepository repository;
    private final PresenceService presence;
    private final ProxySettingsGateway proxySettings;
    private final Messages messages;
    private final PluginConfig config;
    private final FeedbackService feedback;
    private final LuckPermsGateway luckPerms;
    private final Cache<UUID, UUID> lastMessage = Caffeine.newBuilder().expireAfterWrite(java.time.Duration.ofMinutes(10)).build();
    private final Cache<UUID, Integer> offlineCountCache;
    private final Cache<InboxPageKey, List<FriendRepository.OfflineMessage>> offlinePageCache;

    public SocialMessageService(
        ProxyServer server,
        FriendRepository repository,
        PresenceService presence,
        ProxySettingsGateway proxySettings,
        Messages messages,
        PluginConfig config,
        FeedbackService feedback,
        LuckPermsGateway luckPerms
    ) {
        this.server = server;
        this.repository = repository;
        this.presence = presence;
        this.proxySettings = proxySettings;
        this.messages = messages;
        this.config = config;
        this.feedback = feedback;
        this.luckPerms = luckPerms;
        this.offlineCountCache = Caffeine.newBuilder().expireAfterWrite(java.time.Duration.ofSeconds(10)).build();
        this.offlinePageCache = Caffeine.newBuilder().expireAfterWrite(java.time.Duration.ofSeconds(10)).build();
    }

    public void message(Player sender, String targetName, String message) {
        Language language = this.proxySettings.language(sender);
        String normalized = normalizeMessage(message);
        if (normalized.isBlank()) {
            send(sender, language, "message.empty");
            return;
        }
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.areFriends(sender.getUniqueId(), target.uuid()).thenCompose(areFriends -> {
                if (!areFriends) {
                    send(sender, language, "friend.not-friends", target);
                    return done();
                }
                return this.repository.settings(target.uuid()).thenCompose(settings -> {
                    if (!settings.allowFriendMessages() || settings.mutedAllFriends()) {
                        send(sender, language, "message.disabled", target);
                        return done();
                    }
                    Optional<Player> onlineTarget = this.server.getPlayer(target.uuid());
                    if (onlineTarget.isPresent()) {
                        deliver(sender, onlineTarget.get(), normalized);
                        return done();
                    }
                    CompletableFuture<Integer> limit = onlineTarget
                        .map(player -> CompletableFuture.completedFuture(this.luckPerms.offlineMessageLimit(player)))
                        .orElseGet(() -> this.luckPerms.offlineMessageLimit(target.uuid()));
                    return limit.thenCompose(max -> this.repository.storeOfflineMessage(sender.getUniqueId(), target.uuid(), normalized, max))
                        .thenAccept(_ -> {
                            invalidateOfflineInbox(target.uuid());
                            send(sender, language, "message.offline-stored", target);
                        }).exceptionally(throwable -> {
                            if (isOfflineMessageLimit(throwable)) {
                                send(sender, language, "message.offline-full", target);
                            } else {
                                send(sender, language, "message.offline-failed", target);
                            }
                            return null;
                        });
                });
            });
        });
    }

    public void reply(Player sender, String message) {
        UUID target = this.lastMessage.getIfPresent(sender.getUniqueId());
        if (target == null) {
            send(sender, this.proxySettings.language(sender), "message.no-reply");
            return;
        }
        this.server.getPlayer(target).ifPresentOrElse(
            player -> this.message(sender, player.getUsername(), message),
            () -> send(sender, this.proxySettings.language(sender), "message.reply-offline")
        );
    }

    public void broadcast(Player sender, String message) {
        Language language = this.proxySettings.language(sender);
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
                        this.feedback.send(targetPlayer, this.proxySettings.language(targetPlayer), "friend-broadcast-received", Map.of(
                            "player_name", Messages.escape(sender.getUsername())
                        ), this.proxySettings.resolvers(
                            this.proxySettings.playerResolver(sender, ""),
                            this.proxySettings.messageResolver(sender.getUniqueId(), normalized)
                        ));
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

    public void notifyOfflineMessages(Player player) {
        pendingOfflineCount(player.getUniqueId()).thenAccept(count -> {
            if (count <= 0) {
                return;
            }
            Language language = this.proxySettings.language(player);
            this.feedback.send(player, language, "offline-message-notice", Map.of(
                "count", String.valueOf(count),
                "command", this.config.commands().primary(),
                "inbox_command", "/" + this.config.commands().primary() + " inbox"
            ));
        });
    }

    public void inbox(Player player, int page) {
        Language language = this.proxySettings.language(player);
        int pageSize = this.config.friends().pageSize();
        pendingOfflineCount(player.getUniqueId()).thenCompose(total -> {
            if (total <= 0) {
                send(player, language, "offline.empty");
                return done();
            }
            int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
            int safePage = Math.max(1, Math.min(page, pages));
            return offlinePage(player.getUniqueId(), safePage).thenCompose(messages -> this.proxySettings.loadMany(messages.stream()
                .map(FriendRepository.OfflineMessage::senderUuid)
                .toList()
            ).thenRun(() -> {
                player.sendMessage(this.messages.component(language, "offline.inbox-header", Map.of(
                    "page", String.valueOf(safePage),
                    "pages", String.valueOf(pages),
                    "total", String.valueOf(total),
                    "count", String.valueOf(total),
                    "command", this.config.commands().primary()
                )));
                for (FriendRepository.OfflineMessage message : messages) {
                    player.sendMessage(this.messages.component(language, "offline.inbox-entry", Map.of(
                        "id", String.valueOf(message.id()),
                        "player_name", Messages.escape(message.senderName()),
                        "message", message.message(),
                        "date", DATE_FORMAT.format(message.createdAt()),
                        "read_command", "/" + this.config.commands().primary() + " inbox read " + message.id(),
                        "reply_command", "/" + this.config.commands().primary() + " msg " + message.senderName() + " "
                    ), this.proxySettings.resolvers(
                        this.proxySettings.playerResolver(message.senderUuid(), message.senderName(), ""),
                        this.proxySettings.messageResolver(message.senderUuid(), message.message())
                    )));
                }
            }));
        });
    }

    public void markInboxRead(Player player, String idText) {
        Language language = this.proxySettings.language(player);
        long id;
        try {
            id = Long.parseLong(idText);
        } catch (NumberFormatException exception) {
            send(player, language, "offline.not-found");
            return;
        }
        this.repository.markOfflineMessageRead(player.getUniqueId(), id).thenAccept(read -> {
            if (!read) {
                send(player, language, "offline.not-found");
                return;
            }
            invalidateOfflineInbox(player.getUniqueId());
            send(player, language, "offline.read");
        });
    }

    public void clearInbox(Player player) {
        Language language = this.proxySettings.language(player);
        this.repository.markAllOfflineMessagesRead(player.getUniqueId()).thenAccept(count -> {
            invalidateOfflineInbox(player.getUniqueId());
            this.messages.send(player, language, "offline.clear", Map.of("count", String.valueOf(count)));
        });
    }

    public CompletableFuture<List<String>> recentInboxIds(UUID player) {
        List<String> ids = this.offlinePageCache.asMap().entrySet().stream()
            .filter(entry -> entry.getKey().player().equals(player))
            .flatMap(entry -> entry.getValue().stream())
            .map(message -> String.valueOf(message.id()))
            .distinct()
            .sorted()
            .toList();
        return CompletableFuture.completedFuture(ids);
    }

    private void deliver(Player sender, Player target, String message) {
        this.lastMessage.put(sender.getUniqueId(), target.getUniqueId());
        this.lastMessage.put(target.getUniqueId(), sender.getUniqueId());
        this.feedback.send(target, this.proxySettings.language(target), "friend-message-received", Map.of(
            "player_name", Messages.escape(sender.getUsername())
        ), this.proxySettings.resolvers(
            this.proxySettings.playerResolver(sender, ""),
            this.proxySettings.messageResolver(sender.getUniqueId(), message)
        ));
        this.feedback.send(sender, this.proxySettings.language(sender), "friend-message-sent", Map.of(
            "player_name", Messages.escape(target.getUsername())
        ), this.proxySettings.resolvers(
            this.proxySettings.playerResolver(target, ""),
            this.proxySettings.messageResolver(sender.getUniqueId(), message)
        ));
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
        return normalized;
    }

    private CompletableFuture<Integer> pendingOfflineCount(UUID player) {
        Integer cached = this.offlineCountCache.getIfPresent(player);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.repository.pendingOfflineMessageCount(player).thenApply(count -> {
            this.offlineCountCache.put(player, count);
            return count;
        });
    }

    private CompletableFuture<List<FriendRepository.OfflineMessage>> offlinePage(UUID player, int page) {
        InboxPageKey key = new InboxPageKey(player, page);
        List<FriendRepository.OfflineMessage> cached = this.offlinePageCache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.repository.offlineMessages(player, page, this.config.friends().pageSize()).thenApply(messages -> {
            this.offlinePageCache.put(key, messages);
            return messages;
        });
    }

    private void invalidateOfflineInbox(UUID player) {
        this.offlineCountCache.invalidate(player);
        this.offlinePageCache.asMap().keySet().removeIf(key -> key.player().equals(player));
    }

    private static boolean isOfflineMessageLimit(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof IllegalStateException && "offline-message-limit".equals(current.getMessage());
    }

    private void send(Player player, Language language, String key) {
        this.messages.send(player, language, key, Map.of());
    }

    private void send(Player player, Language language, String key, String placeholder, String value) {
        this.messages.send(player, language, key, Map.of(placeholder, Messages.escape(value)));
    }

    private void send(Player player, Language language, String key, Profile actor) {
        this.messages.send(player, language, key, Map.of("player_name", Messages.escape(actor.username())), this.playerResolver(actor));
    }

    private TagResolver playerResolver(Profile profile) {
        return this.proxySettings.playerResolver(profile.uuid(), profile.username(), profile.lastKnownPrefix());
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }

    private record InboxPageKey(UUID player, int page) {
    }
}
