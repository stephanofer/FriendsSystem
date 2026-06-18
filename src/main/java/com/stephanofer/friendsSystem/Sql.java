package com.stephanofer.friendsSystem;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class Sql {

    private Sql() {
    }

    public static byte[] uuidBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        byte[] bytes = resultSet.getBytes(column);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static FriendPair pair(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? new FriendPair(first, second) : new FriendPair(second, first);
    }
}
