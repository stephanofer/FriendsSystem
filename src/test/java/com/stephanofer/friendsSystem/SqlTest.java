package com.stephanofer.friendsSystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqlTest {

    @Test
    void uuidBytesUseBinary16Order() {
        UUID uuid = UUID.fromString("cf11286a-d43e-4f7c-bbd5-1b4f653392c7");
        ByteBuffer expected = ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits());

        assertArrayEquals(expected.array(), Sql.uuidBytes(uuid));
    }

    @Test
    void pairAlwaysOrdersUuids() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(new FriendPair(low, high), Sql.pair(high, low));
        assertEquals(new FriendPair(low, high), Sql.pair(low, high));
    }

    @Test
    void profilesSchemaKeepsUsernameAsLookupNotIdentity() throws IOException {
        String migration = new String(
            SqlTest.class.getResourceAsStream("/db/migration/V1__create_friends_system.sql").readAllBytes(),
            StandardCharsets.UTF_8
        );

        assertFalse(migration.contains("UNIQUE KEY `uk_profiles_username_lower`"));
        assertTrue(migration.contains("KEY `idx_profiles_username_lower` (`username_lower`)"));
    }
}
