package com.stephanofer.friendsSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginConfigTest {

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
}
