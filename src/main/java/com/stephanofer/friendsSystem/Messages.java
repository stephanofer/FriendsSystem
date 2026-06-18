package com.stephanofer.friendsSystem;

import com.velocitypowered.api.proxy.Player;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Messages {

    private static final GeneralSettings MESSAGE_SETTINGS = GeneralSettings.builder()
        .setRouteSeparator('/')
        .build();
    private static final String MESSAGE_VERSION_ROUTE = "config-version";
    private static final LoaderSettings MESSAGE_LOADER_SETTINGS = LoaderSettings.builder()
        .setAutoUpdate(true)
        .build();
    private static final UpdaterSettings MESSAGE_UPDATER_SETTINGS = UpdaterSettings.builder()
        .setVersioning(new BasicVersioning(MESSAGE_VERSION_ROUTE))
        .build();

    private final YamlDocument spanish;
    private final YamlDocument english;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Messages(YamlDocument spanish, YamlDocument english) {
        this.spanish = spanish;
        this.english = english;
    }

    public static Messages load(Path dataDirectory) throws IOException {
        Path messagesDirectory = dataDirectory.resolve("messages");
        Files.createDirectories(messagesDirectory);
        return new Messages(
            loadDocument(messagesDirectory.resolve("es.yml"), "/messages/es.yml"),
            loadDocument(messagesDirectory.resolve("en.yml"), "/messages/en.yml")
        );
    }

    public Component component(Language language, String key, Map<String, String> placeholders) {
        return this.miniMessage.deserialize(this.raw(language, key, placeholders));
    }

    public String raw(Language language, String key, Map<String, String> placeholders) {
        String value = this.document(language).getString(key);
        if (value == null || value.isBlank()) {
            value = key;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return value;
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    public void send(Player player, Language language, String key, Map<String, String> placeholders) {
        player.sendMessage(this.component(language, key, placeholders));
    }

    private YamlDocument document(Language language) {
        return language == Language.ES ? this.spanish : this.english;
    }

    private static YamlDocument loadDocument(Path path, String resource) throws IOException {
        try (InputStream defaults = bundledResource(resource)) {
            return YamlDocument.create(
                path.toFile(),
                defaults,
                MESSAGE_SETTINGS,
                MESSAGE_LOADER_SETTINGS,
                MESSAGE_UPDATER_SETTINGS
            );
        }
    }

    private static InputStream bundledResource(String path) {
        return Objects.requireNonNull(Messages.class.getResourceAsStream(path), () -> "Missing bundled resource: " + path);
    }
}
