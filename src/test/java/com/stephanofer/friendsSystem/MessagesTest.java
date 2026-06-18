package com.stephanofer.friendsSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessagesTest {

    @TempDir
    private Path tempDir;

    @Test
    void escapesMiniMessageTagsFromPlayerText() {
        assertEquals("hello \\<click:run_command:'/op me'>", Messages.escape("hello <click:run_command:'/op me'>"));
    }

    @Test
    void escapesBackslashesBeforeTags() {
        assertEquals("\\\\path \\<red>", Messages.escape("\\path <red>"));
    }

    @Test
    void fallsBackToBundledDefaultsWhenExistingMessageFileMissesNewKeys() throws Exception {
        Path messages = this.tempDir.resolve("messages");
        Files.createDirectories(messages);
        Files.writeString(messages.resolve("es.yml"), "help: '<gray>old help</gray>'\n");

        Messages loaded = Messages.load(this.tempDir);

        assertEquals(
            "<gradient:#6fffe9:#5bc0be><bold>FriendsSystem</bold></gradient> <gray>Comandos disponibles</gray>",
            loaded.raw(Language.ES, "help.header", Map.of("command", "friends"))
        );
    }
}
