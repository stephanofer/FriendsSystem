package com.stephanofer.friendsSystem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FriendSettingsTest {

    @Test
    void usesSafeDefaults() {
        FriendSettings settings = FriendSettings.from(Map.of());

        assertTrue(settings.showOnlineStatus());
        assertTrue(settings.showActivity());
        assertTrue(settings.allowFriendMessages());
        assertTrue(settings.allowFriendBroadcasts());
        assertTrue(settings.showConnectionNotifications());
        assertFalse(settings.mutedAllFriends());
    }

    @Test
    void appliesPersistedOverrides() {
        FriendSettings settings = FriendSettings.from(Map.of(
            "show_online_status", "false",
            "show_activity", "false",
            "allow_friend_messages", "false",
            "allow_friend_broadcasts", "false",
            "show_connection_notifications", "false",
            "muted_all_friends", "true"
        ));

        assertFalse(settings.showOnlineStatus());
        assertFalse(settings.showActivity());
        assertFalse(settings.allowFriendMessages());
        assertFalse(settings.allowFriendBroadcasts());
        assertFalse(settings.showConnectionNotifications());
        assertTrue(settings.mutedAllFriends());
    }
}
