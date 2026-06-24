package com.stephanofer.friendsSystem;

import com.hera.craftkit.database.Database;
import com.hera.craftkit.database.TransactionOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FriendRepository {

    private final Database database;
    private final PluginConfig config;
    private final Cache<UUID, FriendSettings> settingsCache;

    public FriendRepository(Database database, PluginConfig config) {
        this.database = database;
        this.config = config;
        this.settingsCache = Caffeine.newBuilder().expireAfterWrite(config.cache().settingsTtl()).build();
    }

    public CompletableFuture<Void> upsertProfile(UUID uuid, String username, String prefix, String primaryGroup) {
        return this.database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (player_uuid, username, username_lower, last_known_prefix, last_known_primary_group)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE username = VALUES(username), username_lower = VALUES(username_lower),
                    last_known_prefix = VALUES(last_known_prefix), last_known_primary_group = VALUES(last_known_primary_group)
                """.formatted(this.database.table("profiles")))) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                statement.setString(2, username);
                statement.setString(3, username.toLowerCase());
                statement.setString(4, prefix);
                statement.setString(5, primaryGroup);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> markLastSeen(UUID uuid) {
        return this.database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + this.database.table("profiles") + " SET last_seen_at = CURRENT_TIMESTAMP WHERE player_uuid = ?"
            )) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<Profile>> findProfileByName(String username) {
        return this.database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + this.database.table("profiles") + " WHERE username_lower = ? ORDER BY updated_at DESC, created_at DESC LIMIT 1"
            )) {
                statement.setString(1, username.toLowerCase());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readProfile(result)) : Optional.empty();
                }
            }
        });
    }

    public CompletableFuture<List<Profile>> identityConflicts(String username, UUID currentUuid) {
        return this.database.query(connection -> {
            List<Profile> conflicts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + this.database.table("profiles") + " WHERE username_lower = ? AND player_uuid <> ? ORDER BY updated_at DESC, created_at DESC"
            )) {
                statement.setString(1, username.toLowerCase());
                statement.setBytes(2, Sql.uuidBytes(currentUuid));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        conflicts.add(readProfile(result));
                    }
                }
            }
            return conflicts;
        });
    }

    public CompletableFuture<List<Profile>> friends(UUID uuid) {
        return this.database.query(connection -> {
            List<UUID> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_low_uuid, player_high_uuid FROM %s WHERE player_low_uuid = ? OR player_high_uuid = ?
                """.formatted(this.database.table("edges")))) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                statement.setBytes(2, Sql.uuidBytes(uuid));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID low = Sql.uuid(result, "player_low_uuid");
                        UUID high = Sql.uuid(result, "player_high_uuid");
                        ids.add(low.equals(uuid) ? high : low);
                    }
                }
            }
            if (ids.isEmpty()) {
                return List.of();
            }
            return profiles(connection, ids);
        });
    }

    public CompletableFuture<Integer> friendCount(UUID uuid) {
        return this.database.query(connection -> countFriends(connection, uuid));
    }

    public CompletableFuture<Boolean> areFriends(UUID first, UUID second) {
        FriendPair pair = Sql.pair(first, second);
        return this.database.query(connection -> edgeExists(connection, pair));
    }

    public CompletableFuture<Boolean> isBlocked(UUID blocker, UUID blocked) {
        return this.database.query(connection -> blockExists(connection, blocker, blocked));
    }

    public CompletableFuture<Boolean> createRequest(UUID sender, UUID target) {
        Instant expiresAt = Instant.now().plus(this.config.friends().requestExpiration());
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO %s (sender_uuid, target_uuid, expires_at) VALUES (?, ?, ?)
                """.formatted(this.database.table("requests")))) {
                statement.setBytes(1, Sql.uuidBytes(sender));
                statement.setBytes(2, Sql.uuidBytes(target));
                statement.setTimestamp(3, Timestamp.from(expiresAt));
                return statement.executeUpdate();
            }
        }).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<Boolean> requestExists(UUID sender, UUID target) {
        return this.database.query(connection -> requestExists(connection, sender, target));
    }

    public CompletableFuture<Boolean> acceptRequest(UUID sender, UUID target, int senderLimit, int targetLimit) {
        return this.database.transaction(TransactionOptions.serializable(), connection -> {
            if (!requestExists(connection, sender, target)) {
                return false;
            }
            if (blockExists(connection, sender, target) || blockExists(connection, target, sender)) {
                throw new IllegalStateException("blocked");
            }
            if (countFriends(connection, sender) >= senderLimit || countFriends(connection, target) >= targetLimit) {
                throw new IllegalStateException("friend-limit");
            }
            deleteAnyRequest(connection, sender, target);
            FriendPair pair = Sql.pair(sender, target);
            if (!edgeExists(connection, pair)) {
                try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO %s (player_low_uuid, player_high_uuid) VALUES (?, ?)
                    """.formatted(this.database.table("edges")))) {
                    statement.setBytes(1, Sql.uuidBytes(pair.low()));
                    statement.setBytes(2, Sql.uuidBytes(pair.high()));
                    statement.executeUpdate();
                }
            }
            return true;
        });
    }

    public CompletableFuture<Boolean> deleteRequest(UUID sender, UUID target) {
        return this.database.update(connection -> deleteActiveRequest(connection, sender, target)).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<Boolean> removeFriend(UUID first, UUID second) {
        FriendPair pair = Sql.pair(first, second);
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + this.database.table("edges") + " WHERE player_low_uuid = ? AND player_high_uuid = ?"
            )) {
                statement.setBytes(1, Sql.uuidBytes(pair.low()));
                statement.setBytes(2, Sql.uuidBytes(pair.high()));
                return statement.executeUpdate();
            }
        }).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<Void> deleteRequestsBetween(UUID first, UUID second) {
        return this.database.execute(connection -> {
            deleteAnyRequest(connection, first, second);
            deleteAnyRequest(connection, second, first);
        });
    }

    public CompletableFuture<Boolean> block(UUID blocker, UUID blocked) {
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO %s (blocker_uuid, blocked_uuid) VALUES (?, ?)
                """.formatted(this.database.table("blocks")))) {
                statement.setBytes(1, Sql.uuidBytes(blocker));
                statement.setBytes(2, Sql.uuidBytes(blocked));
                return statement.executeUpdate();
            }
        }).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<Boolean> unblock(UUID blocker, UUID blocked) {
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + this.database.table("blocks") + " WHERE blocker_uuid = ? AND blocked_uuid = ?"
            )) {
                statement.setBytes(1, Sql.uuidBytes(blocker));
                statement.setBytes(2, Sql.uuidBytes(blocked));
                return statement.executeUpdate();
            }
        }).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<List<PendingRequest>> incomingRequests(UUID target) {
        return requests(target, true);
    }

    public CompletableFuture<List<PendingRequest>> outgoingRequests(UUID sender) {
        return requests(sender, false);
    }

    public CompletableFuture<List<Profile>> incomingRequestProfiles(UUID target) {
        return requestProfiles(target, true);
    }

    public CompletableFuture<List<Profile>> outgoingRequestProfiles(UUID sender) {
        return requestProfiles(sender, false);
    }

    public CompletableFuture<List<Profile>> blockedPlayers(UUID blocker) {
        return this.database.query(connection -> {
            List<Profile> profiles = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.* FROM %s b JOIN %s p ON p.player_uuid = b.blocked_uuid
                WHERE b.blocker_uuid = ?
                ORDER BY p.username_lower ASC
                LIMIT ?
                """.formatted(this.database.table("blocks"), this.database.table("profiles")))) {
                statement.setBytes(1, Sql.uuidBytes(blocker));
                statement.setInt(2, this.config.commands().suggestions().queryMaxResults());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        profiles.add(readProfile(result));
                    }
                }
            }
            return profiles;
        });
    }

    public CompletableFuture<FriendSettings> settings(UUID uuid) {
        FriendSettings cached = this.settingsCache.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.database.query(connection -> {
            Map<String, String> values = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT setting_key, setting_value FROM " + this.database.table("settings") + " WHERE player_uuid = ?"
            )) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        values.put(result.getString("setting_key"), result.getString("setting_value"));
                    }
                }
            }
            FriendSettings settings = FriendSettings.from(values);
            this.settingsCache.put(uuid, settings);
            return settings;
        });
    }

    public CompletableFuture<Void> setSetting(UUID uuid, String key, boolean value) {
        return this.database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (player_uuid, setting_key, setting_value) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """.formatted(this.database.table("settings")))) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                statement.setString(2, key);
                statement.setString(3, Boolean.toString(value));
                statement.executeUpdate();
            }
        }).thenRun(() -> this.settingsCache.invalidate(uuid));
    }

    public CompletableFuture<Void> storeOfflineMessage(UUID sender, UUID target, String message, int limit) {
        Instant expiresAt = Instant.now().plus(this.config.friends().offlineMessageExpiration());
        return this.database.transaction(TransactionOptions.serializable(), connection -> {
            int pending = pendingOfflineCount(connection, target);
            if (pending >= limit) {
                throw new IllegalStateException("offline-message-limit");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO %s (sender_uuid, target_uuid, message, expires_at) VALUES (?, ?, ?, ?)
                """.formatted(this.database.table("offline_messages")))) {
                statement.setBytes(1, Sql.uuidBytes(sender));
                statement.setBytes(2, Sql.uuidBytes(target));
                statement.setString(3, message);
                statement.setTimestamp(4, Timestamp.from(expiresAt));
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> pendingOfflineMessageCount(UUID target) {
        return this.database.query(connection -> pendingOfflineCount(connection, target));
    }

    public CompletableFuture<List<OfflineMessage>> offlineMessages(UUID target, int page, int pageSize) {
        int offset = Math.max(0, page - 1) * pageSize;
        return this.database.query(connection -> {
            List<OfflineMessage> messages = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT m.id, m.sender_uuid, COALESCE(p.username, 'Jugador') AS sender_name, p.last_known_prefix, m.message, m.created_at
                FROM %s m LEFT JOIN %s p ON p.player_uuid = m.sender_uuid
                WHERE m.target_uuid = ? AND m.delivered_at IS NULL AND m.expires_at > CURRENT_TIMESTAMP
                ORDER BY m.created_at ASC LIMIT ? OFFSET ?
                """.formatted(this.database.table("offline_messages"), this.database.table("profiles")))) {
                statement.setBytes(1, Sql.uuidBytes(target));
                statement.setInt(2, pageSize);
                statement.setInt(3, offset);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        messages.add(new OfflineMessage(
                            result.getLong("id"),
                            Sql.uuid(result, "sender_uuid"),
                            result.getString("sender_name"),
                            result.getString("last_known_prefix"),
                            result.getString("message"),
                            result.getTimestamp("created_at").toInstant()
                        ));
                    }
                }
            }
            return messages;
        });
    }

    public CompletableFuture<Boolean> markOfflineMessageRead(UUID target, long id) {
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE %s SET delivered_at = CURRENT_TIMESTAMP
                WHERE id = ? AND target_uuid = ? AND delivered_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """.formatted(this.database.table("offline_messages")))) {
                statement.setLong(1, id);
                statement.setBytes(2, Sql.uuidBytes(target));
                return statement.executeUpdate();
            }
        }).thenApply(rows -> rows > 0);
    }

    public CompletableFuture<Integer> markAllOfflineMessagesRead(UUID target) {
        return this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE %s SET delivered_at = CURRENT_TIMESTAMP
                WHERE target_uuid = ? AND delivered_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """.formatted(this.database.table("offline_messages")))) {
                statement.setBytes(1, Sql.uuidBytes(target));
                return statement.executeUpdate();
            }
        });
    }

    public void deleteExpiredRequests() {
        this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + this.database.table("requests") + " WHERE expires_at <= CURRENT_TIMESTAMP"
            )) {
                return statement.executeUpdate();
            }
        });
    }

    public void deleteExpiredOfflineMessages() {
        this.database.update(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + this.database.table("offline_messages") + " WHERE expires_at <= CURRENT_TIMESTAMP"
            )) {
                return statement.executeUpdate();
            }
        });
    }

    private CompletableFuture<List<PendingRequest>> requests(UUID uuid, boolean incoming) {
        return this.database.query(connection -> {
            List<PendingRequest> requests = new ArrayList<>();
            String subjectColumn = incoming ? "target_uuid" : "sender_uuid";
            String profileJoinColumn = incoming ? "sender_uuid" : "target_uuid";
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT r.sender_uuid, r.target_uuid, r.created_at, r.expires_at, p.username, p.last_known_prefix
                FROM %s r JOIN %s p ON p.player_uuid = r.%s
                WHERE r.%s = ? AND r.expires_at > CURRENT_TIMESTAMP
                ORDER BY r.created_at DESC
                """.formatted(this.database.table("requests"), this.database.table("profiles"), profileJoinColumn, subjectColumn))) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        requests.add(new PendingRequest(
                            Sql.uuid(result, "sender_uuid"),
                            Sql.uuid(result, "target_uuid"),
                            result.getString("username"),
                            result.getString("last_known_prefix"),
                            result.getTimestamp("created_at").toInstant(),
                            result.getTimestamp("expires_at").toInstant()
                        ));
                    }
                }
            }
            return requests;
        });
    }

    private CompletableFuture<List<Profile>> requestProfiles(UUID uuid, boolean incoming) {
        return this.database.query(connection -> {
            List<Profile> profiles = new ArrayList<>();
            String subjectColumn = incoming ? "target_uuid" : "sender_uuid";
            String profileJoinColumn = incoming ? "sender_uuid" : "target_uuid";
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.* FROM %s r JOIN %s p ON p.player_uuid = r.%s
                WHERE r.%s = ? AND r.expires_at > CURRENT_TIMESTAMP
                ORDER BY p.username_lower ASC
                LIMIT ?
                """.formatted(this.database.table("requests"), this.database.table("profiles"), profileJoinColumn, subjectColumn))) {
                statement.setBytes(1, Sql.uuidBytes(uuid));
                statement.setInt(2, this.config.commands().suggestions().queryMaxResults());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        profiles.add(readProfile(result));
                    }
                }
            }
            return profiles;
        });
    }

    private List<Profile> profiles(Connection connection, List<UUID> ids) throws java.sql.SQLException {
        String placeholders = String.join(",", ids.stream().map(_ -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM " + this.database.table("profiles") + " WHERE player_uuid IN (" + placeholders + ") ORDER BY username_lower ASC"
        )) {
            for (int index = 0; index < ids.size(); index++) {
                statement.setBytes(index + 1, Sql.uuidBytes(ids.get(index)));
            }
            List<Profile> profiles = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    profiles.add(readProfile(result));
                }
            }
            return profiles;
        }
    }

    private static Profile readProfile(ResultSet result) throws java.sql.SQLException {
        Timestamp lastSeen = result.getTimestamp("last_seen_at");
        return new Profile(
            Sql.uuid(result, "player_uuid"),
            result.getString("username"),
            result.getString("username_lower"),
            result.getString("last_known_prefix"),
            result.getString("last_known_primary_group"),
            lastSeen == null ? null : lastSeen.toInstant()
        );
    }

    private boolean edgeExists(Connection connection, FriendPair pair) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM " + this.database.table("edges") + " WHERE player_low_uuid = ? AND player_high_uuid = ?"
        )) {
            statement.setBytes(1, Sql.uuidBytes(pair.low()));
            statement.setBytes(2, Sql.uuidBytes(pair.high()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean blockExists(Connection connection, UUID blocker, UUID blocked) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM " + this.database.table("blocks") + " WHERE blocker_uuid = ? AND blocked_uuid = ?"
        )) {
            statement.setBytes(1, Sql.uuidBytes(blocker));
            statement.setBytes(2, Sql.uuidBytes(blocked));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean requestExists(Connection connection, UUID sender, UUID target) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM " + this.database.table("requests") + " WHERE sender_uuid = ? AND target_uuid = ? AND expires_at > CURRENT_TIMESTAMP"
        )) {
            statement.setBytes(1, Sql.uuidBytes(sender));
            statement.setBytes(2, Sql.uuidBytes(target));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private int deleteActiveRequest(Connection connection, UUID sender, UUID target) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + this.database.table("requests") + " WHERE sender_uuid = ? AND target_uuid = ? AND expires_at > CURRENT_TIMESTAMP"
        )) {
            statement.setBytes(1, Sql.uuidBytes(sender));
            statement.setBytes(2, Sql.uuidBytes(target));
            return statement.executeUpdate();
        }
    }

    private int deleteAnyRequest(Connection connection, UUID sender, UUID target) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + this.database.table("requests") + " WHERE sender_uuid = ? AND target_uuid = ?"
        )) {
            statement.setBytes(1, Sql.uuidBytes(sender));
            statement.setBytes(2, Sql.uuidBytes(target));
            return statement.executeUpdate();
        }
    }

    private int countFriends(Connection connection, UUID uuid) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM " + this.database.table("edges") + " WHERE player_low_uuid = ? OR player_high_uuid = ?"
        )) {
            statement.setBytes(1, Sql.uuidBytes(uuid));
            statement.setBytes(2, Sql.uuidBytes(uuid));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int pendingOfflineCount(Connection connection, UUID target) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM " + this.database.table("offline_messages") + " WHERE target_uuid = ? AND delivered_at IS NULL AND expires_at > CURRENT_TIMESTAMP"
        )) {
            statement.setBytes(1, Sql.uuidBytes(target));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public record OfflineMessage(long id, UUID senderUuid, String senderName, String lastKnownPrefix, String message, Instant createdAt) {
    }
}
