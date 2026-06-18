package com.stephanofer.friendsSystem;

public record ActivityState(String serverId, String activityKey, String map, String mode, int partySize, long startedAt) {
}
