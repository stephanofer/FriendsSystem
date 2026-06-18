package com.stephanofer.friendsSystem;

import java.time.Instant;
import java.util.UUID;

public record FriendListEntry(
    UUID uuid,
    String username,
    String prefix,
    String primaryGroup,
    Instant lastSeenAt,
    PresenceState presence,
    ActivityState activity
) {
}
