package com.stephanofer.friendsSystem;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record PluginConfig(
    Database database,
    NetworkPlayerSettings networkPlayerSettings,
    Redis redis,
    Friends friends,
    Limits limits,
    Commands commands,
    Cache cache,
    Feedback feedback
) {

    private static final String CONFIG_VERSION_ROUTE = "config-version";
    private static final LoaderSettings CONFIG_LOADER_SETTINGS = LoaderSettings.builder()
        .setAutoUpdate(true)
        .build();
    private static final UpdaterSettings CONFIG_UPDATER_SETTINGS = UpdaterSettings.builder()
        .setVersioning(new BasicVersioning(CONFIG_VERSION_ROUTE))
        .build();

    public static PluginConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = dataDirectory.resolve("config.yml");
        try (InputStream defaults = resource("/config.yml")) {
            YamlDocument document = YamlDocument.create(
                configPath.toFile(),
                defaults,
                CONFIG_LOADER_SETTINGS,
                DumperSettings.DEFAULT,
                CONFIG_UPDATER_SETTINGS
            );
            return new PluginConfig(
                new Database(
                    document.getString("database.host"),
                    document.getInt("database.port"),
                    document.getString("database.database"),
                    document.getString("database.username"),
                    document.getString("database.password"),
                    document.getString("database.table-prefix")
                ),
                new NetworkPlayerSettings(
                    document.getBoolean("network-player-settings.enabled"),
                    document.getString("network-player-settings.table-prefix"),
                    Language.fromCode(document.getString("network-player-settings.default-language"))
                ),
                new Redis(
                    document.getString("redis.host"),
                    document.getInt("redis.port"),
                    document.getInt("redis.database"),
                    document.getString("redis.username"),
                    document.getString("redis.password"),
                    document.getBoolean("redis.ssl"),
                    document.getString("redis.key-prefix"),
                    document.getString("redis.environment"),
                    document.getString("redis.server-id")
                ),
                new Friends(
                    duration(document.getString("friends.request-expiration")),
                    duration(document.getString("friends.offline-message-expiration")),
                    document.getInt("friends.page-size"),
                    document.getInt("friends.message-max-length")
                ),
                new Limits(
                    new LimitGroup(
                        positive(document.getInt("limits.friends.default"), positive(document.getInt("friends.default-limit"), 50)),
                        permissionLimits(document, "limits.friends.permissions")
                    ),
                    new LimitGroup(
                        positive(document.getInt("limits.offline-messages.default"), positive(document.getInt("friends.max-offline-messages"), 20)),
                        permissionLimits(document, "limits.offline-messages.permissions")
                    )
                ),
                new Commands(
                    document.getString("commands.primary").trim(),
                    aliases(document.getStringList("commands.aliases")),
                    aliases(document.getStringList("commands.message-aliases")),
                    aliases(document.getStringList("commands.reply-aliases")),
                    new Suggestions(
                        duration(document.getString("commands.suggestions.cache-ttl")),
                        positive(document.getInt("commands.suggestions.empty-input-max-results"), 20),
                        positive(document.getInt("commands.suggestions.filtered-max-results"), 50),
                        positive(document.getInt("commands.suggestions.query-max-results"), 100)
                    )
                ),
                new Cache(
                    duration(document.getString("cache.friends-ttl")),
                    duration(document.getString("cache.settings-ttl")),
                    duration(document.getString("cache.activity-page-ttl")),
                    duration(document.getString("cache.presence-ttl"))
                ),
                new Feedback(feedbackActions(document))
            );
        }
    }

    private static InputStream resource(String path) {
        return Objects.requireNonNull(PluginConfig.class.getResourceAsStream(path), () -> "Missing bundled resource: " + path);
    }

    public static Duration duration(String value) {
        String text = value.trim().toLowerCase();
        long amount = Long.parseLong(text.substring(0, text.length() - 1));
        return switch (text.charAt(text.length() - 1)) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> Duration.parse(value);
        };
    }

    public static List<String> aliases(List<String> values) {
        return values.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
    }

    public static List<String> commandLabels(String primary, List<String> aliases) {
        String normalizedPrimary = primary == null || primary.isBlank() ? "friends" : primary.trim();
        List<String> labels = new java.util.ArrayList<>();
        labels.add(normalizedPrimary);
        for (String alias : aliases(aliases)) {
            if (!alias.equalsIgnoreCase(normalizedPrimary)) {
                labels.add(alias);
            }
        }
        return List.copyOf(labels);
    }

    public static Map<String, FeedbackAction> feedbackActions(YamlDocument document) {
        Map<String, FeedbackAction> actions = new HashMap<>();
        if (!document.isSection("feedback.actions")) {
            return Map.of();
        }
        for (Object key : document.getSection("feedback.actions").getKeys()) {
            String action = String.valueOf(key);
            List<FeedbackOutput> outputs = new ArrayList<>();
            for (Map<?, ?> output : document.getMapList("feedback.actions." + action + ".outputs", List.of())) {
                FeedbackOutput parsed = feedbackOutput(output);
                if (parsed != null) {
                    outputs.add(parsed);
                }
            }
            actions.put(action, new FeedbackAction(List.copyOf(outputs)));
        }
        return Map.copyOf(actions);
    }

    private static List<PermissionLimit> permissionLimits(YamlDocument document, String path) {
        List<PermissionLimit> limits = new ArrayList<>();
        for (Map<?, ?> entry : document.getMapList(path, List.of())) {
            String permission = string(entry, "permission", "").trim();
            int limit = positive(integer(entry, "limit", 0), 0);
            if (!permission.isEmpty() && limit > 0) {
                limits.add(new PermissionLimit(permission, limit));
            }
        }
        return List.copyOf(limits);
    }

    private static FeedbackOutput feedbackOutput(Map<?, ?> output) {
        String type = string(output, "type", "CHAT").toUpperCase(Locale.ROOT);
        String message = string(output, "message", "");
        String sound = string(output, "sound", "minecraft:block.note_block.pling");
        String source = string(output, "source", "MASTER").toUpperCase(Locale.ROOT);
        float volume = decimal(output, "volume", 1.0f);
        float pitch = decimal(output, "pitch", 1.0f);
        String title = string(output, "title", message);
        String subtitle = string(output, "subtitle", "");
        return new FeedbackOutput(type, message, sound, source, volume, pitch, title, subtitle);
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static float decimal(Map<?, ?> map, String key, float fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    public record Database(String host, int port, String database, String username, String password, String tablePrefix) {}
    public record NetworkPlayerSettings(boolean enabled, String tablePrefix, Language defaultLanguage) {}
    public record Redis(String host, int port, int database, String username, String password, boolean ssl, String keyPrefix, String environment, String serverId) {}
    public record Friends(Duration requestExpiration, Duration offlineMessageExpiration, int pageSize, int messageMaxLength) {}
    public record Limits(LimitGroup friends, LimitGroup offlineMessages) {}
    public record LimitGroup(int defaultLimit, List<PermissionLimit> permissions) {}
    public record PermissionLimit(String permission, int limit) {}
    public record Commands(String primary, List<String> aliases, List<String> messageAliases, List<String> replyAliases, Suggestions suggestions) {
        public List<String> labels() {
            return commandLabels(this.primary, this.aliases);
        }
    }
    public record Suggestions(Duration cacheTtl, int emptyInputMaxResults, int filteredMaxResults, int queryMaxResults) {}
    public record Cache(Duration friendsTtl, Duration settingsTtl, Duration activityPageTtl, Duration presenceTtl) {}
    public record Feedback(Map<String, FeedbackAction> actions) {
        public FeedbackAction action(String key) {
            return this.actions.getOrDefault(key, FeedbackAction.chat(defaultFeedbackMessage(key)));
        }

        public FeedbackAction fallback(String key) {
            return FeedbackAction.chat(defaultFeedbackMessage(key));
        }

        private static String defaultFeedbackMessage(String action) {
            return switch (action) {
                case "request-received" -> "request.received";
                case "request-sent" -> "request.sent";
                case "request-accepted" -> "request.accepted";
                case "request-accepted-target" -> "request.accepted-target";
                case "request-denied" -> "request.denied";
                case "request-denied-target" -> "request.denied-target";
                case "request-withdrawn" -> "request.withdrawn";
                case "friend-removed" -> "friend.removed";
                case "block-added" -> "block.added";
                case "block-removed" -> "block.removed";
                case "friend-message-sent" -> "message.sent";
                case "friend-message-received" -> "message.received";
                case "friend-broadcast-received" -> "message.broadcast-received";
                case "friend-join" -> "notify.join";
                case "friend-quit" -> "notify.quit";
                case "offline-message-notice" -> "offline.notice";
                default -> action;
            };
        }
    }
    public record FeedbackAction(List<FeedbackOutput> outputs) {
        public static FeedbackAction chat(String messageKey) {
            return new FeedbackAction(List.of(new FeedbackOutput("CHAT", messageKey, "", "MASTER", 1.0f, 1.0f, messageKey, "")));
        }
    }
    public record FeedbackOutput(String type, String message, String sound, String source, float volume, float pitch, String title, String subtitle) {}

    public List<String> socialSettingKeys() {
        return List.of(
            "show_online_status",
            "show_activity",
            "allow_friend_messages",
            "allow_friend_broadcasts",
            "show_connection_notifications",
            "muted_all_friends"
        );
    }
}
