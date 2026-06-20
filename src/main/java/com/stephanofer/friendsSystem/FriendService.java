package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendService {

    private final ProxyServer server;
    private final FriendRepository repository;
    private final PresenceService presence;
    private final LuckPermsGateway luckPerms;
    private final LanguageService languages;
    private final Messages messages;
    private final PluginConfig config;
    private final FeedbackService feedback;
    private final FriendSuggestionCache suggestions;
    private final Cache<UUID, List<Profile>> friendCache;

    public FriendService(
        ProxyServer server,
        FriendRepository repository,
        PresenceService presence,
        LuckPermsGateway luckPerms,
        LanguageService languages,
        Messages messages,
        PluginConfig config,
        FeedbackService feedback,
        FriendSuggestionCache suggestions
    ) {
        this.server = server;
        this.repository = repository;
        this.presence = presence;
        this.luckPerms = luckPerms;
        this.languages = languages;
        this.messages = messages;
        this.config = config;
        this.feedback = feedback;
        this.suggestions = suggestions;
        this.friendCache = Caffeine.newBuilder().expireAfterWrite(config.cache().friendsTtl()).build();
    }

    public void add(Player sender, String targetName) {
        Language language = this.languages.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            if (target.uuid().equals(sender.getUniqueId())) {
                send(sender, language, "friend.self");
                return done();
            }
            int limit = this.luckPerms.friendLimit(sender);
            return this.repository.friendCount(sender.getUniqueId()).thenCompose(count -> {
                if (count >= limit) {
                    send(sender, language, "friend.limit", "limit", String.valueOf(limit));
                    return done();
                }
                return this.repository.areFriends(sender.getUniqueId(), target.uuid()).thenCompose(areFriends -> {
                    if (areFriends) {
                        send(sender, language, "friend.already", "player", target.username());
                        return done();
                    }
                    return this.repository.isBlocked(target.uuid(), sender.getUniqueId()).thenCombine(
                        this.repository.isBlocked(sender.getUniqueId(), target.uuid()),
                        (blockedByTarget, blockedBySender) -> blockedByTarget || blockedBySender
                    ).thenCompose(blocked -> {
                        if (blocked) {
                            send(sender, language, "friend.not-found", "player", targetName);
                            return done();
                        }
                        return this.repository.requestExists(target.uuid(), sender.getUniqueId()).thenCompose(reverseRequest -> {
                            if (reverseRequest) {
                                send(sender, language, "request.crossed", "player", target.username());
                                return done();
                            }
                        return this.repository.createRequest(sender.getUniqueId(), target.uuid()).thenAccept(created -> {
                            if (!created) {
                                send(sender, language, "request.already", "player", target.username());
                                return;
                            }
                            this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                            feedback(sender, language, "request-sent", "player", target.username());
                            this.server.getPlayer(target.uuid()).ifPresent(targetPlayer -> this.notifyRequest(sender, targetPlayer));
                        });
                        });
                    });
                });
            });
        });
    }

    public void accept(Player target, String senderName) {
        Language language = this.languages.language(target);
        this.resolveTarget(senderName).thenCompose(optionalSender -> {
            if (optionalSender.isEmpty()) {
                send(target, language, "friend.not-found", "player", senderName);
                return done();
            }
            Profile sender = optionalSender.get();
            int targetLimit = this.luckPerms.friendLimit(target);
            CompletableFuture<Integer> senderLimit = this.server.getPlayer(sender.uuid())
                .map(player -> CompletableFuture.completedFuture(this.luckPerms.friendLimit(player)))
                .orElseGet(() -> this.luckPerms.friendLimit(sender.uuid()));
            return senderLimit.thenCompose(limit -> this.repository.acceptRequest(sender.uuid(), target.getUniqueId(), limit, targetLimit))
                .thenAccept(accepted -> {
                    if (!accepted) {
                        send(target, language, "request.none-incoming", "player", sender.username());
                        return;
                    }
                    this.invalidate(target.getUniqueId(), sender.uuid());
                    this.suggestions.invalidate(target.getUniqueId(), sender.uuid());
                    feedback(target, language, "request-accepted", "player", sender.username());
                    this.server.getPlayer(sender.uuid()).ifPresent(player -> feedback(
                        player,
                        this.languages.language(player),
                        "request-accepted-target",
                        "player",
                        target.getUsername()
                    ));
                }).exceptionally(throwable -> {
                    send(target, language, "friend.limit-target", "player", sender.username());
                    return null;
                });
        });
    }

    public void deny(Player target, String senderName) {
        Language language = this.languages.language(target);
        this.resolveTarget(senderName).thenCompose(optionalSender -> {
            if (optionalSender.isEmpty()) {
                send(target, language, "friend.not-found", "player", senderName);
                return done();
            }
            Profile sender = optionalSender.get();
            return this.repository.deleteRequest(sender.uuid(), target.getUniqueId()).thenAccept(deleted -> {
                if (!deleted) {
                    send(target, language, "request.none-incoming", "player", sender.username());
                    return;
                }
                this.suggestions.invalidate(target.getUniqueId(), sender.uuid());
                feedback(target, language, "request-denied", "player", sender.username());
                this.server.getPlayer(sender.uuid()).ifPresent(player -> feedback(
                    player,
                    this.languages.language(player),
                    "request-denied-target",
                    "player",
                    target.getUsername()
                ));
            });
        });
    }

    public void withdraw(Player sender, String targetName) {
        Language language = this.languages.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.deleteRequest(sender.getUniqueId(), target.uuid()).thenAccept(deleted -> {
                if (!deleted) {
                    send(sender, language, "request.none-outgoing", "player", target.username());
                    return;
                }
                this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                feedback(sender, language, "request-withdrawn", "player", target.username());
            });
        });
    }

    public void remove(Player sender, String targetName) {
        Language language = this.languages.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.removeFriend(sender.getUniqueId(), target.uuid()).thenAccept(removed -> {
                if (removed) {
                    this.invalidate(sender.getUniqueId(), target.uuid());
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                }
                if (removed) {
                    feedback(sender, language, "friend-removed", "player", target.username());
                    return;
                }
                send(sender, language, "friend.not-friends", "player", target.username());
            });
        });
    }

    public void block(Player sender, String targetName) {
        Language language = this.languages.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            if (target.uuid().equals(sender.getUniqueId())) {
                send(sender, language, "friend.self");
                return done();
            }
            return this.repository.block(sender.getUniqueId(), target.uuid()).thenCompose(blocked -> {
                if (!blocked) {
                    send(sender, language, "block.already", "player", target.username());
                    return done();
                }
                return this.repository.deleteRequestsBetween(sender.getUniqueId(), target.uuid()).thenCompose(__ ->
                this.repository.removeFriend(sender.getUniqueId(), target.uuid()).thenAccept(___ -> {
                    this.invalidate(sender.getUniqueId(), target.uuid());
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                    feedback(sender, language, "block-added", "player", target.username());
                }));
            });
        });
    }

    public void unblock(Player sender, String targetName) {
        Language language = this.languages.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.unblock(sender.getUniqueId(), target.uuid()).thenAccept(removed -> {
                if (removed) {
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                    feedback(sender, language, "block-removed", "player", target.username());
                    return;
                }
                send(sender, language, "block.none", "player", target.username());
            });
        });
    }

    public void list(Player viewer, int page) {
        Language language = this.languages.language(viewer);
        this.friends(viewer.getUniqueId()).thenCompose(friends -> {
            if (friends.isEmpty()) {
                send(viewer, language, "list.empty", Map.of("command", this.config.commands().primary()));
                return done();
            }
            int pageSize = this.config.friends().pageSize();
            int maxPage = Math.max(1, (int) Math.ceil(friends.size() / (double) pageSize));
            int currentPage = Math.max(1, Math.min(page, maxPage));
            int from = (currentPage - 1) * pageSize;
            int to = Math.min(from + pageSize, friends.size());
            List<Profile> visible = friends.subList(from, to);
            List<UUID> ids = visible.stream().map(Profile::uuid).toList();
            return this.presence.loadPage(viewer.getUniqueId(), currentPage, ids).thenCompose(states -> {
                List<CompletableFuture<FriendSettings>> settingFutures = visible.stream()
                    .map(profile -> this.repository.settings(profile.uuid()))
                    .toList();
                return CompletableFuture.allOf(settingFutures.toArray(CompletableFuture[]::new)).thenAccept(_ -> {
                viewer.sendMessage(this.messages.component(language, "list.header", Map.of(
                    "page", String.valueOf(currentPage),
                    "pages", String.valueOf(maxPage),
                    "total", String.valueOf(friends.size()),
                    "command", this.config.commands().primary()
                )));
                for (int index = 0; index < visible.size(); index++) {
                    Profile profile = visible.get(index);
                    FriendSettings settings = settingFutures.get(index).join();
                    PresenceService.PresenceActivity state = applyPrivacy(states.get(profile.uuid()), settings);
                    viewer.sendMessage(this.messages.component(language, "list.entry", placeholders(profile, state)));
                }
                });
            });
        });
    }

    public void pending(Player player) {
        Language language = this.languages.language(player);
        CompletableFuture<List<PendingRequest>> incoming = this.repository.incomingRequests(player.getUniqueId());
        CompletableFuture<List<PendingRequest>> outgoing = this.repository.outgoingRequests(player.getUniqueId());
        incoming.thenCombine(outgoing, (in, out) -> {
            player.sendMessage(this.messages.component(language, "pending.header", Map.of()));
            if (in.isEmpty()) {
                player.sendMessage(this.messages.component(language, "pending.incoming-empty", Map.of()));
            } else {
                player.sendMessage(this.messages.component(language, "pending.incoming-title", Map.of("count", String.valueOf(in.size()))));
                for (PendingRequest request : in) {
                    player.sendMessage(this.messages.component(language, "pending.incoming-entry", pendingPlaceholders(request)));
                }
            }
            if (out.isEmpty()) {
                player.sendMessage(this.messages.component(language, "pending.outgoing-empty", Map.of()));
            } else {
                player.sendMessage(this.messages.component(language, "pending.outgoing-title", Map.of("count", String.valueOf(out.size()))));
                for (PendingRequest request : out) {
                    player.sendMessage(this.messages.component(language, "pending.outgoing-entry", pendingPlaceholders(request)));
                }
            }
            return null;
        });
    }

    public void toggle(Player player, String setting) {
        Language language = this.languages.language(player);
        if (!this.config.socialSettingKeys().contains(setting)) {
            send(player, language, "settings.unknown", "setting", setting);
            return;
        }
        this.repository.settings(player.getUniqueId()).thenCompose(settings -> {
            boolean newValue = switch (setting) {
                case "show_online_status" -> !settings.showOnlineStatus();
                case "show_activity" -> !settings.showActivity();
                case "allow_friend_messages" -> !settings.allowFriendMessages();
                case "allow_friend_broadcasts" -> !settings.allowFriendBroadcasts();
                case "show_connection_notifications" -> !settings.showConnectionNotifications();
                case "muted_all_friends" -> !settings.mutedAllFriends();
                default -> true;
            };
            return this.repository.setSetting(player.getUniqueId(), setting, newValue).thenAccept(_ ->
                send(player, language, "settings.changed", Map.of("setting", setting, "value", Boolean.toString(newValue)))
            );
        });
    }

    public void notifyFriendsConnection(Player player, boolean joined) {
        this.repository.findProfileByName(player.getUsername()).thenAccept(profile -> this.notifyFriendsConnection(
            player,
            joined,
            profile.orElse(new Profile(player.getUniqueId(), player.getUsername(), player.getUsername().toLowerCase(), "", "", null))
        ));
    }

    public void notifyFriendsConnection(Player player, boolean joined, Profile actor) {
        this.repository.settings(player.getUniqueId()).thenAccept(playerSettings -> {
            if (!playerSettings.showOnlineStatus()) {
                return;
            }
            this.friends(player.getUniqueId()).thenAccept(friends -> {
                for (Profile friend : friends) {
                    this.server.getPlayer(friend.uuid()).ifPresent(target -> this.repository.settings(target.getUniqueId()).thenAccept(settings -> {
                        if (!settings.showConnectionNotifications()) {
                            return;
                        }
                        this.feedback.send(target, this.languages.language(target), joined ? "friend-join" : "friend-quit", placeholders(actor, null));
                    }));
                }
            });
        });
    }

    public CompletableFuture<List<Profile>> friends(UUID uuid) {
        List<Profile> cached = this.friendCache.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.repository.friends(uuid).thenApply(friends -> {
            this.friendCache.put(uuid, friends);
            return friends;
        });
    }

    private CompletableFuture<Optional<Profile>> resolveTarget(String name) {
        Optional<Player> online = this.server.getPlayer(name);
        if (online.isPresent()) {
            Player player = online.get();
            this.luckPerms.snapshot(player).thenAccept(snapshot -> this.repository.upsertProfile(
                player.getUniqueId(), player.getUsername(), snapshot.prefix(), snapshot.primaryGroup()
            ));
            return CompletableFuture.completedFuture(Optional.of(new Profile(
                player.getUniqueId(), player.getUsername(), player.getUsername().toLowerCase(), "", "", null
            )));
        }
        return this.repository.findProfileByName(name);
    }

    private void notifyRequest(Player sender, Player target) {
        Language language = this.languages.language(target);
        this.feedback.send(target, language, "request-received", Map.of(
            "player", sender.getUsername(),
            "accept", "/friend accept " + sender.getUsername(),
            "deny", "/friend deny " + sender.getUsername()
        ));
    }

    private void feedback(Player player, Language language, String action, String placeholder, String value) {
        this.feedback.send(player, language, action, Map.of(placeholder, Messages.escape(value)));
    }

    private Map<String, String> placeholders(Profile profile, PresenceService.PresenceActivity state) {
        String status = "offline";
        String server = "";
        String activity = "";
        if (state != null && state.presence() != null) {
            status = "online";
            server = state.presence().server();
            activity = server;
        }
        if (state != null && state.activity() != null) {
            ActivityState rich = state.activity();
            activity = rich.mode() + (rich.map() == null || rich.map().isBlank() ? "" : " - " + rich.map());
        }
        Map<String, String> map = new HashMap<>();
        map.put("player", Messages.escape(profile.username()));
        map.put("prefix", profile.lastKnownPrefix() == null ? "" : profile.lastKnownPrefix());
        map.put("group", profile.lastKnownPrimaryGroup() == null ? "" : Messages.escape(profile.lastKnownPrimaryGroup()));
        map.put("message_command", "/" + this.config.commands().primary() + " msg " + profile.username() + " ");
        map.put("status", status);
        map.put("server", server);
        map.put("activity", activity);
        return map;
    }

    private static PresenceService.PresenceActivity applyPrivacy(PresenceService.PresenceActivity state, FriendSettings settings) {
        if (state == null || !settings.showOnlineStatus()) {
            return null;
        }
        if (!settings.showActivity()) {
            return new PresenceService.PresenceActivity(state.presence(), null);
        }
        return state;
    }

    private Map<String, String> pendingPlaceholders(PendingRequest request) {
        String player = Messages.escape(request.username());
        return Map.of(
            "player", player,
            "accept_command", "/" + this.config.commands().primary() + " accept " + request.username(),
            "deny_command", "/" + this.config.commands().primary() + " deny " + request.username(),
            "withdraw_command", "/" + this.config.commands().primary() + " withdraw " + request.username()
        );
    }

    private void invalidate(UUID first, UUID second) {
        this.friendCache.invalidate(first);
        this.friendCache.invalidate(second);
    }

    private void send(Player player, Language language, String key) {
        this.messages.send(player, language, key, Map.of());
    }

    private void send(Player player, Language language, String key, String placeholder, String value) {
        this.messages.send(player, language, key, Map.of(placeholder, Messages.escape(value)));
    }

    private void send(Player player, Language language, String key, Map<String, String> placeholders) {
        this.messages.send(player, language, key, placeholders);
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
