package com.stephanofer.friendsSystem;

import java.util.Map;

public record FriendSettings(
    boolean showOnlineStatus,
    boolean showActivity,
    boolean allowFriendMessages,
    boolean allowFriendBroadcasts,
    boolean showConnectionNotifications,
    boolean mutedAllFriends
) {

    public static FriendSettings defaults() {
        return new FriendSettings(true, true, true, true, true, false);
    }

    public static FriendSettings from(Map<String, String> values) {
        FriendSettings defaults = defaults();
        return new FriendSettings(
            bool(values, "show_online_status", defaults.showOnlineStatus),
            bool(values, "show_activity", defaults.showActivity),
            bool(values, "allow_friend_messages", defaults.allowFriendMessages),
            bool(values, "allow_friend_broadcasts", defaults.allowFriendBroadcasts),
            bool(values, "show_connection_notifications", defaults.showConnectionNotifications),
            bool(values, "muted_all_friends", defaults.mutedAllFriends)
        );
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
