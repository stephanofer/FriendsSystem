package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class FriendService {

    private final ProxyServer server;
    private final FriendRepository repository;
    private final PresenceService presence;
    private final LuckPermsGateway luckPerms;
    private final ProxySettingsGateway proxySettings;
    private final Messages messages;
    private final PluginConfig config;
    private final FeedbackService feedback;
    private final FriendSuggestionCache suggestions;
    private final DebugLogger debug;
    private final CommandCooldowns cooldowns;
    private final Cache<UUID, List<Profile>> friendCache;

    public FriendService(
        ProxyServer server,
        FriendRepository repository,
        PresenceService presence,
        LuckPermsGateway luckPerms,
        ProxySettingsGateway proxySettings,
        Messages messages,
        PluginConfig config,
        FeedbackService feedback,
        FriendSuggestionCache suggestions,
        DebugLogger debug,
        CommandCooldowns cooldowns
    ) {
        this.server = server;
        this.repository = repository;
        this.presence = presence;
        this.luckPerms = luckPerms;
        this.proxySettings = proxySettings;
        this.messages = messages;
        this.config = config;
        this.feedback = feedback;
        this.suggestions = suggestions;
        this.debug = debug;
        this.cooldowns = cooldowns;
        this.friendCache = Caffeine.newBuilder().expireAfterWrite(config.cache().friendsTtl()).build();
    }

    public void add(Player sender, String targetName) {
        Language language = this.proxySettings.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
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
                        send(sender, language, "friend.already", target);
                        return done();
                    }
                    return this.repository.isBlocked(target.uuid(), sender.getUniqueId()).thenCombine(
                        this.repository.isBlocked(sender.getUniqueId(), target.uuid()),
                        (blockedByTarget, blockedBySender) -> blockedByTarget || blockedBySender
                    ).thenCompose(blocked -> {
                        if (blocked) {
                            send(sender, language, "friend.not-found", "player_name", targetName);
                            return done();
                        }
                        return this.repository.requestExists(target.uuid(), sender.getUniqueId()).thenCompose(reverseRequest -> {
                            if (reverseRequest) {
                                send(sender, language, "request.crossed", target);
                                return done();
                            }
                        return this.repository.createRequest(sender.getUniqueId(), target.uuid()).thenAccept(created -> {
                            if (!created) {
                                send(sender, language, "request.already", target);
                                return;
                            }
                            this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                            feedback(sender, language, "request-sent", target);
                            this.server.getPlayer(target.uuid()).ifPresent(targetPlayer -> this.notifyRequest(sender, targetPlayer));
                        });
                        });
                    });
                });
            });
        });
    }

    public void accept(Player target, String senderName) {
        Language language = this.proxySettings.language(target);
        this.resolveTarget(senderName).thenCompose(optionalSender -> {
            if (optionalSender.isEmpty()) {
                send(target, language, "friend.not-found", "player_name", senderName);
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
                        send(target, language, "request.none-incoming", sender);
                        return;
                    }
                    this.invalidate(target.getUniqueId(), sender.uuid());
                    this.suggestions.invalidate(target.getUniqueId(), sender.uuid());
                    feedback(target, language, "request-accepted", sender);
                    this.server.getPlayer(sender.uuid()).ifPresent(player -> this.playerProfile(target).thenAccept(targetProfile -> feedback(
                        player,
                        this.proxySettings.language(player),
                        "request-accepted-target",
                        targetProfile
                    )));
                }).exceptionally(throwable -> {
                    send(target, language, "friend.limit-target", sender);
                    return null;
                });
        });
    }

    public void deny(Player target, String senderName) {
        Language language = this.proxySettings.language(target);
        this.resolveTarget(senderName).thenCompose(optionalSender -> {
            if (optionalSender.isEmpty()) {
                send(target, language, "friend.not-found", "player_name", senderName);
                return done();
            }
            Profile sender = optionalSender.get();
            return this.repository.deleteRequest(sender.uuid(), target.getUniqueId()).thenAccept(deleted -> {
                if (!deleted) {
                    send(target, language, "request.none-incoming", sender);
                    return;
                }
                this.suggestions.invalidate(target.getUniqueId(), sender.uuid());
                    feedback(target, language, "request-denied", sender);
                this.server.getPlayer(sender.uuid()).ifPresent(player -> this.playerProfile(target).thenAccept(targetProfile -> feedback(
                    player,
                    this.proxySettings.language(player),
                    "request-denied-target",
                    targetProfile
                )));
            });
        });
    }

    public void withdraw(Player sender, String targetName) {
        Language language = this.proxySettings.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.deleteRequest(sender.getUniqueId(), target.uuid()).thenAccept(deleted -> {
                if (!deleted) {
                    send(sender, language, "request.none-outgoing", target);
                    return;
                }
                this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                feedback(sender, language, "request-withdrawn", target);
            });
        });
    }

    public void remove(Player sender, String targetName) {
        Language language = this.proxySettings.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.removeFriend(sender.getUniqueId(), target.uuid()).thenAccept(removed -> {
                if (removed) {
                    this.invalidate(sender.getUniqueId(), target.uuid());
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                }
                if (removed) {
                    feedback(sender, language, "friend-removed", target);
                    return;
                }
                send(sender, language, "friend.not-friends", target);
            });
        });
    }

    public void block(Player sender, String targetName) {
        Language language = this.proxySettings.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            if (target.uuid().equals(sender.getUniqueId())) {
                send(sender, language, "friend.self");
                return done();
            }
            return this.repository.block(sender.getUniqueId(), target.uuid()).thenCompose(blocked -> {
                if (!blocked) {
                    send(sender, language, "block.already", target);
                    return done();
                }
                return this.repository.deleteRequestsBetween(sender.getUniqueId(), target.uuid()).thenCompose(__ ->
                this.repository.removeFriend(sender.getUniqueId(), target.uuid()).thenAccept(___ -> {
                    this.invalidate(sender.getUniqueId(), target.uuid());
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                    feedback(sender, language, "block-added", target);
                }));
            });
        });
    }

    public void unblock(Player sender, String targetName) {
        Language language = this.proxySettings.language(sender);
        this.resolveTarget(targetName).thenCompose(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                send(sender, language, "friend.not-found", "player_name", targetName);
                return done();
            }
            Profile target = optionalTarget.get();
            return this.repository.unblock(sender.getUniqueId(), target.uuid()).thenAccept(removed -> {
                if (removed) {
                    this.suggestions.invalidate(sender.getUniqueId(), target.uuid());
                    feedback(sender, language, "block-removed", target);
                    return;
                }
                send(sender, language, "block.none", target);
            });
        });
    }

    public void list(Player viewer, int page) {
        Language language = this.proxySettings.language(viewer);
        Duration remaining = this.cooldowns.list(viewer.getUniqueId());
        if (!remaining.isZero()) {
            sendCooldown(viewer, language, remaining);
            return;
        }
        String source = this.friendCache.getIfPresent(viewer.getUniqueId()) == null ? "database" : "friend-cache";
        this.friends(viewer.getUniqueId()).thenCompose(friends -> {
            this.debug.friendList("Friend list loaded", Map.of(
                "viewer", viewer.getUsername(),
                "viewerUuid", viewer.getUniqueId().toString(),
                "source", source,
                "friendCount", String.valueOf(friends.size()),
                "friends", friendSummary(friends)
            ));
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
            return this.proxySettings.loadMany(ids).thenCompose(_ -> this.presence.loadPage(viewer.getUniqueId(), currentPage, ids)).thenCompose(states -> {
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
                    this.debug.friendList("Friend list entry rendered", Map.ofEntries(
                        Map.entry("viewer", viewer.getUsername()),
                        Map.entry("viewerUuid", viewer.getUniqueId().toString()),
                        Map.entry("target", profile.username()),
                        Map.entry("targetUuid", profile.uuid().toString()),
                        Map.entry("targetPrefix", profile.lastKnownPrefix() == null ? "" : profile.lastKnownPrefix()),
                        Map.entry("targetGroup", profile.lastKnownPrimaryGroup() == null ? "" : profile.lastKnownPrimaryGroup()),
                        Map.entry("targetShowOnlineStatus", Boolean.toString(settings.showOnlineStatus())),
                        Map.entry("targetShowActivity", Boolean.toString(settings.showActivity())),
                        Map.entry("liveVelocityOnline", Boolean.toString(this.server.getPlayer(profile.uuid()).isPresent())),
                        Map.entry("presenceVisible", Boolean.toString(state != null && state.presence() != null)),
                        Map.entry("presenceServer", state == null || state.presence() == null ? "" : state.presence().server()),
                        Map.entry("activityVisible", Boolean.toString(state != null && state.activity() != null)),
                        Map.entry("renderedStatus", state == null || state.presence() == null ? "offline" : "online")
                    ));
                    viewer.sendMessage(this.messages.component(language, "list.entry", placeholders(profile, state), this.playerResolver(profile)));
                }
                });
            });
        });
    }

    public void pending(Player player) {
        Language language = this.proxySettings.language(player);
        Duration remaining = this.cooldowns.pending(player.getUniqueId());
        if (!remaining.isZero()) {
            sendCooldown(player, language, remaining);
            return;
        }
        CompletableFuture<List<PendingRequest>> incoming = this.repository.incomingRequests(player.getUniqueId());
        CompletableFuture<List<PendingRequest>> outgoing = this.repository.outgoingRequests(player.getUniqueId());
        incoming.thenCombine(outgoing, PendingLists::new).thenCompose(pending -> {
            List<UUID> ids = new ArrayList<>();
            pending.incoming().forEach(request -> ids.add(request.senderUuid()));
            pending.outgoing().forEach(request -> ids.add(request.targetUuid()));
            return this.proxySettings.loadMany(ids).thenRun(() -> {
            player.sendMessage(this.messages.component(language, "pending.header", Map.of()));
            if (pending.incoming().isEmpty()) {
                player.sendMessage(this.messages.component(language, "pending.incoming-empty", Map.of()));
            } else {
                player.sendMessage(this.messages.component(language, "pending.incoming-title", Map.of("count", String.valueOf(pending.incoming().size()))));
                for (PendingRequest request : pending.incoming()) {
                    player.sendMessage(this.messages.component(language, "pending.incoming-entry", pendingPlaceholders(request), this.playerResolver(request.senderUuid(), request.username(), request.lastKnownPrefix())));
                }
            }
            if (pending.outgoing().isEmpty()) {
                player.sendMessage(this.messages.component(language, "pending.outgoing-empty", Map.of()));
            } else {
                player.sendMessage(this.messages.component(language, "pending.outgoing-title", Map.of("count", String.valueOf(pending.outgoing().size()))));
                for (PendingRequest request : pending.outgoing()) {
                    player.sendMessage(this.messages.component(language, "pending.outgoing-entry", pendingPlaceholders(request), this.playerResolver(request.targetUuid(), request.username(), request.lastKnownPrefix())));
                }
            }
            });
        });
    }

    public void toggle(Player player, String setting) {
        Language language = this.proxySettings.language(player);
        Duration remaining = this.cooldowns.toggle(player.getUniqueId());
        if (!remaining.isZero()) {
            sendCooldown(player, language, remaining);
            return;
        }
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
                {
                    this.debug.settings("Setting changed", Map.of(
                        "player", player.getUsername(),
                        "uuid", player.getUniqueId().toString(),
                        "setting", setting,
                        "value", Boolean.toString(newValue)
                    ));
                    send(player, language, "settings.changed", Map.of("setting", setting, "value", Boolean.toString(newValue)));
                }
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
                this.debug.notifications("Connection notification skipped", Map.of(
                    "actor", player.getUsername(),
                    "joined", Boolean.toString(joined),
                    "actorShowOnlineStatus", Boolean.toString(playerSettings.showOnlineStatus()),
                    "delivered", "false",
                    "reason", "actor-hidden-online-status"
                ));
                return;
            }
            this.friends(player.getUniqueId()).thenAccept(friends -> {
                for (Profile friend : friends) {
                    Optional<Player> targetOptional = this.server.getPlayer(friend.uuid());
                    if (targetOptional.isEmpty()) {
                        this.debug.notifications("Connection notification", Map.of(
                            "actor", player.getUsername(),
                            "target", friend.username(),
                            "joined", Boolean.toString(joined),
                            "actorShowOnlineStatus", Boolean.toString(playerSettings.showOnlineStatus()),
                            "targetOnline", "false",
                            "delivered", "false",
                            "reason", "target-offline"
                        ));
                        continue;
                    }
                    Player target = targetOptional.get();
                    this.repository.settings(target.getUniqueId()).thenAccept(settings -> {
                        boolean delivered = false;
                        String reason = "delivered";
                        if (!settings.showConnectionNotifications()) {
                            reason = "target-disabled-notifications";
                            this.debug.notifications("Connection notification", Map.of(
                                "actor", player.getUsername(),
                                "target", target.getUsername(),
                                "joined", Boolean.toString(joined),
                                "actorShowOnlineStatus", Boolean.toString(playerSettings.showOnlineStatus()),
                                "targetShowConnectionNotifications", Boolean.toString(settings.showConnectionNotifications()),
                                "targetOnline", "true",
                                "delivered", Boolean.toString(delivered),
                                "reason", reason
                            ));
                            return;
                        }
                        delivered = true;
                        this.feedback.send(target, this.proxySettings.language(target), joined ? "friend-join" : "friend-quit", placeholders(actor, null), this.playerResolver(actor));
                        this.debug.notifications("Connection notification", Map.of(
                            "actor", player.getUsername(),
                            "target", target.getUsername(),
                            "joined", Boolean.toString(joined),
                            "actorShowOnlineStatus", Boolean.toString(playerSettings.showOnlineStatus()),
                            "targetShowConnectionNotifications", Boolean.toString(settings.showConnectionNotifications()),
                            "targetOnline", "true",
                            "delivered", Boolean.toString(delivered),
                            "reason", reason
                        ));
                    });
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
            return this.playerProfile(online.get()).thenApply(Optional::of);
        }
        return this.repository.findProfileByName(name);
    }

    private void notifyRequest(Player sender, Player target) {
        Language language = this.proxySettings.language(target);
        this.playerProfile(sender).thenAccept(profile -> this.feedback.send(target, language, "request-received", Map.of(
            "player_name", Messages.escape(profile.username()),
            "accept", "/friend accept " + profile.username(),
            "deny", "/friend deny " + profile.username()
        ), this.playerResolver(profile)));
    }

    private CompletableFuture<Profile> playerProfile(Player player) {
        return this.luckPerms.snapshot(player).thenCompose(snapshot -> this.repository.upsertProfile(
            player.getUniqueId(), player.getUsername(), snapshot.prefix(), snapshot.primaryGroup()
        ).thenApply(_ -> {
            this.debug.prefixes("LuckPerms profile snapshot", Map.of(
                "player", player.getUsername(),
                "uuid", player.getUniqueId().toString(),
                "prefix", snapshot.prefix(),
                "primaryGroup", snapshot.primaryGroup()
            ));
            return new Profile(
                player.getUniqueId(),
                player.getUsername(),
                player.getUsername().toLowerCase(),
                snapshot.prefix(),
                snapshot.primaryGroup(),
                null
            );
        }));
    }

    private void feedback(Player player, Language language, String action, Profile actor) {
        this.feedback.send(player, language, action, Map.of("player_name", Messages.escape(actor.username())), this.playerResolver(actor));
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
        map.put("player_name", Messages.escape(profile.username()));
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
        return Map.of(
            "player_name", Messages.escape(request.username()),
            "accept_command", "/" + this.config.commands().primary() + " accept " + request.username(),
            "deny_command", "/" + this.config.commands().primary() + " deny " + request.username(),
            "withdraw_command", "/" + this.config.commands().primary() + " withdraw " + request.username()
        );
    }

    private static String friendSummary(List<Profile> friends) {
        if (friends.isEmpty()) {
            return "[]";
        }
        return friends.stream()
            .map(profile -> profile.username() + "(" + profile.uuid() + ")")
            .reduce((first, second) -> first + ", " + second)
            .map(value -> "[" + value + "]")
            .orElse("[]");
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

    private void send(Player player, Language language, String key, Profile actor) {
        this.messages.send(player, language, key, Map.of("player_name", Messages.escape(actor.username())), this.playerResolver(actor));
    }

    private void send(Player player, Language language, String key, Map<String, String> placeholders) {
        this.messages.send(player, language, key, placeholders);
    }

    private void sendCooldown(Player player, Language language, Duration remaining) {
        long seconds = Math.max(1, (long) Math.ceil(remaining.toMillis() / 1000.0));
        this.messages.send(player, language, "cooldown", Map.of("seconds", String.valueOf(seconds)));
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }

    private TagResolver playerResolver(Profile profile) {
        return this.proxySettings.playerResolver(profile.uuid(), profile.username(), profile.lastKnownPrefix());
    }

    private TagResolver playerResolver(UUID uuid, String username, String prefix) {
        return this.proxySettings.playerResolver(uuid, username, prefix);
    }

    private record PendingLists(List<PendingRequest> incoming, List<PendingRequest> outgoing) {
    }
}
