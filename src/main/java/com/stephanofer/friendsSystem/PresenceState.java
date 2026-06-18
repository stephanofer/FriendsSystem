package com.stephanofer.friendsSystem;

public record PresenceState(String server, String username, long onlineSince, long lastHeartbeat) {
}
