package com.stephanofer.friendsSystem;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hera.craftkit.database.Database;
import com.velocitypowered.api.proxy.Player;
import java.sql.PreparedStatement;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LanguageService {

    private final Database database;
    private final PluginConfig config;
    private final Cache<UUID, Language> languages = Caffeine.newBuilder().build();

    public LanguageService(Database database, PluginConfig config) {
        this.database = database;
        this.config = config;
    }

    public void loadOnJoin(Player player) {
        this.load(player.getUniqueId(), player.getEffectiveLocale()).thenAccept(language ->
            this.languages.put(player.getUniqueId(), language)
        );
    }

    public Language language(Player player) {
        return Optional.ofNullable(this.languages.getIfPresent(player.getUniqueId()))
            .orElseGet(() -> Language.fromLocale(player.getEffectiveLocale(), this.config.networkPlayerSettings().defaultLanguage()));
    }

    public void evict(UUID uuid) {
        this.languages.invalidate(uuid);
    }

    private CompletableFuture<Language> load(UUID uuid, Locale locale) {
        if (!this.config.networkPlayerSettings().enabled()) {
            return CompletableFuture.completedFuture(Language.fromLocale(locale, this.config.networkPlayerSettings().defaultLanguage()));
        }
        String table = this.config.networkPlayerSettings().tablePrefix() + "player_settings";
        return this.database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT setting_value FROM " + table + " WHERE player_uuid = ? AND setting_key = 'language'"
            )) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Language.fromLocale(locale, this.config.networkPlayerSettings().defaultLanguage());
                    }
                    String value = result.getString("setting_value");
                    if ("auto".equalsIgnoreCase(value)) {
                        return Language.fromLocale(locale, this.config.networkPlayerSettings().defaultLanguage());
                    }
                    return Language.fromCode(value);
                }
            }
        }).exceptionally(throwable -> Language.fromLocale(locale, this.config.networkPlayerSettings().defaultLanguage()));
    }
}
