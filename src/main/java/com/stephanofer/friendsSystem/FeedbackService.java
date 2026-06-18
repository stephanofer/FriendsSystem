package com.stephanofer.friendsSystem;

import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

public final class FeedbackService {

    private final Messages messages;
    private final PluginConfig config;
    private final Logger logger;

    public FeedbackService(Messages messages, PluginConfig config, Logger logger) {
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    public void send(Player player, Language language, String action, Map<String, String> placeholders) {
        PluginConfig.FeedbackAction feedback = this.config.feedback().action(action);
        if (feedback.outputs().stream().anyMatch(output -> "NONE".equalsIgnoreCase(output.type()))) {
            return;
        }
        if (feedback.outputs().isEmpty()) {
            feedback = this.config.feedback().fallback(action);
        }
        if (feedback.outputs().isEmpty()) {
            return;
        }
        for (PluginConfig.FeedbackOutput output : feedback.outputs()) {
            this.sendOutput(player, language, output, placeholders);
        }
    }

    private void sendOutput(Player player, Language language, PluginConfig.FeedbackOutput output, Map<String, String> placeholders) {
        switch (output.type().toUpperCase(Locale.ROOT)) {
            case "CHAT" -> player.sendMessage(this.messages.component(language, output.message(), placeholders));
            case "ACTION_BAR" -> player.sendActionBar(this.messages.component(language, output.message(), placeholders));
            case "TITLE" -> player.showTitle(Title.title(
                this.messages.component(language, output.title(), placeholders),
                output.subtitle().isBlank() ? net.kyori.adventure.text.Component.empty() : this.messages.component(language, output.subtitle(), placeholders)
            ));
            case "SOUND" -> this.playSound(player, output);
            case "CENTER" -> this.logger.warn("Ignoring unsupported CENTER feedback output");
            default -> this.logger.warn("Ignoring unknown feedback output type '{}'", output.type());
        }
    }

    private void playSound(Player player, PluginConfig.FeedbackOutput output) {
        try {
            Sound.Source source = Sound.Source.valueOf(output.source().toUpperCase(Locale.ROOT));
            player.playSound(
                Sound.sound(Key.key(output.sound()), source, output.volume(), output.pitch()),
                Sound.Emitter.self()
            );
        } catch (IllegalArgumentException exception) {
            this.logger.warn("Ignoring invalid feedback sound '{}' with source '{}'", output.sound(), output.source());
        }
    }
}
