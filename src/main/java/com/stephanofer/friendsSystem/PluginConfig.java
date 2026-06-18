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
import java.util.List;
import java.util.Objects;

public record PluginConfig(
    Database database,
    NetworkPlayerSettings networkPlayerSettings,
    Redis redis,
    Friends friends,
    Permissions permissions,
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
                    document.getInt("friends.default-limit"),
                    document.getInt("friends.max-offline-messages"),
                    duration(document.getString("friends.offline-message-expiration")),
                    document.getInt("friends.page-size"),
                    document.getInt("friends.message-max-length")
                ),
                new Permissions(
                    document.getString("permissions.limit-prefix"),
                    document.getInt("permissions.default-limit"),
                    document.getInt("permissions.max-limit-scan")
                ),
                new Commands(
                    document.getString("commands.primary").trim(),
                    aliases(document.getStringList("commands.aliases")),
                    aliases(document.getStringList("commands.message-aliases")),
                    aliases(document.getStringList("commands.reply-aliases"))
                ),
                new Cache(
                    duration(document.getString("cache.friends-ttl")),
                    duration(document.getString("cache.settings-ttl")),
                    duration(document.getString("cache.activity-page-ttl")),
                    duration(document.getString("cache.presence-ttl"))
                ),
                new Feedback(
                    document.getBoolean("feedback.actionbar"),
                    document.getBoolean("feedback.sounds")
                )
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

    public record Database(String host, int port, String database, String username, String password, String tablePrefix) {}
    public record NetworkPlayerSettings(boolean enabled, String tablePrefix, Language defaultLanguage) {}
    public record Redis(String host, int port, int database, String username, String password, boolean ssl, String keyPrefix, String environment, String serverId) {}
    public record Friends(Duration requestExpiration, int defaultLimit, int maxOfflineMessages, Duration offlineMessageExpiration, int pageSize, int messageMaxLength) {}
    public record Permissions(String limitPrefix, int defaultLimit, int maxLimitScan) {}
    public record Commands(String primary, List<String> aliases, List<String> messageAliases, List<String> replyAliases) {
        public List<String> labels() {
            return commandLabels(this.primary, this.aliases);
        }
    }
    public record Cache(Duration friendsTtl, Duration settingsTtl, Duration activityPageTtl, Duration presenceTtl) {}
    public record Feedback(boolean actionbar, boolean sounds) {}

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
