package com.stephanofer.friendsSystem;

import java.time.Instant;
import java.util.UUID;

public record Profile(
    UUID uuid,
    String username,
    String usernameLower,
    String lastKnownPrefix,
    String lastKnownPrimaryGroup,
    Instant lastSeenAt
) {
}
