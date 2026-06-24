package com.stephanofer.friendsSystem;

public record PresenceState(String proxyId, String server, String username, long onlineSince, long lastHeartbeat) {
}
