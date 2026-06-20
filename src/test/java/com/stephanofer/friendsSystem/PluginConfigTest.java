package com.stephanofer.friendsSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void parsesCompactDurations() {
        assertEquals(Duration.ofSeconds(5), PluginConfig.duration("5s"));
        assertEquals(Duration.ofMinutes(2), PluginConfig.duration("2m"));
        assertEquals(Duration.ofHours(3), PluginConfig.duration("3h"));
        assertEquals(Duration.ofDays(7), PluginConfig.duration("7d"));
    }

    @Test
    void normalizesAliases() {
        assertEquals(List.of("fmsg", "fm"), PluginConfig.aliases(List.of(" fmsg ", "", "fm", "fmsg")));
    }

    @Test
    void buildsCommandLabelsFromPrimaryAndAliases() {
        assertEquals(List.of("friends", "friend", "amigos"), PluginConfig.commandLabels(" friends ", List.of("friend", "friends", "amigos")));
    }

    @Test
    void loadsSuggestionLimitsAndFeedbackOutputs() throws Exception {
        PluginConfig config = PluginConfig.load(this.tempDir);

        assertEquals(Duration.ofSeconds(5), config.commands().suggestions().cacheTtl());
        assertEquals(20, config.commands().suggestions().emptyInputMaxResults());
        assertEquals(50, config.commands().suggestions().filteredMaxResults());
        assertEquals(100, config.commands().suggestions().queryMaxResults());
        assertEquals(50, config.limits().friends().defaultLimit());
        assertEquals("friendssystem.limit.vip", config.limits().friends().permissions().get(1).permission());
        assertEquals(100, config.limits().friends().permissions().get(1).limit());
        assertEquals(20, config.limits().offlineMessages().defaultLimit());
        assertEquals("friendssystem.offline-messages.vip", config.limits().offlineMessages().permissions().get(1).permission());
        assertEquals(50, config.limits().offlineMessages().permissions().get(1).limit());
        assertEquals("CHAT", config.feedback().action("request-received").outputs().getFirst().type());
        assertTrue(config.feedback().action("request-received").outputs().stream().anyMatch(output -> output.type().equals("SOUND")));
    }

    @Test
    void mapsMissingFeedbackActionsToMessageKeys() throws Exception {
        PluginConfig config = PluginConfig.load(this.tempDir);

        assertEquals("request.sent", config.feedback().fallback("request-sent").outputs().getFirst().message());
    }
}
