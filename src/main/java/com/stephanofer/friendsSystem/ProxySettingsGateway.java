package com.stephanofer.friendsSystem;

import com.stephanofer.proxysettings.api.ProxySettingsApi;
import com.stephanofer.proxysettings.api.ProxySettingsProvider;
import com.velocitypowered.api.proxy.Player;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class ProxySettingsGateway {

    private final ProxySettingsApi api;
    private final PluginConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ProxySettingsGateway(PluginConfig config) {
        this.api = ProxySettingsProvider.api();
        this.config = config;
    }

    public CompletableFuture<Void> load(Player player) {
        return this.api.load(player.getUniqueId()).thenApply(_ -> null);
    }

    public CompletableFuture<Void> loadMany(Collection<UUID> playerIds) {
        return this.api.loadMany(playerIds).thenApply(_ -> null);
    }

    public Language language(Player player) {
        return switch (this.api.resolvedLanguage(player.getUniqueId(), player.getEffectiveLocale())) {
            case SPANISH -> Language.ES;
            case ENGLISH -> Language.EN;
        };
    }

    public void evict(UUID uuid) {
        this.api.invalidate(uuid);
    }

    public Component styledMessage(UUID playerId, String message) {
        Component raw = Component.text(message == null ? "" : message);
        return this.api.formatChatMessage(playerId, raw).orElse(raw);
    }

    public TagResolver playerResolver(UUID playerId, String username, String prefix) {
        Component prefixComponent = this.prefix(prefix);
        Component nick = this.api.formattedNick(playerId, username);
        Component flag = this.api.countryFlag(playerId);
        Component identity = this.miniMessage.deserialize(
            this.config.display().playerIdentityFormat(),
            TagResolver.resolver(
                Placeholder.component("player_prefix", prefixComponent),
                Placeholder.component("country_flag", flag),
                Placeholder.component("player_nick", nick)
            )
        );
        return TagResolver.resolver(
            Placeholder.component("player_identity", identity),
            Placeholder.component("player_prefix", prefixComponent),
            Placeholder.component("country_flag", flag),
            Placeholder.component("player_nick", nick)
        );
    }

    public TagResolver playerResolver(Player player, String prefix) {
        return this.playerResolver(player.getUniqueId(), player.getUsername(), prefix);
    }

    public TagResolver messageResolver(UUID senderId, String message) {
        return Placeholder.component("message", this.styledMessage(senderId, message));
    }

    public TagResolver resolvers(TagResolver... resolvers) {
        return TagResolver.resolver(resolvers);
    }

    private Component prefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        return this.miniMessage.deserialize(prefix);
    }
}
