package com.stephanofer.friendsSystem;

import java.time.Instant;
import java.util.UUID;

public record PendingRequest(UUID senderUuid, UUID targetUuid, String username, Instant createdAt, Instant expiresAt) {
}
