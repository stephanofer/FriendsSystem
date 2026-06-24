CREATE TABLE IF NOT EXISTS `${tablePrefix}profiles` (
    `player_uuid` BINARY(16) NOT NULL PRIMARY KEY,
    `username` VARCHAR(16) NOT NULL,
    `username_lower` VARCHAR(16) NOT NULL,
    `last_known_prefix` VARCHAR(128) NULL,
    `last_known_primary_group` VARCHAR(64) NULL,
    `last_seen_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_profiles_username_lower` (`username_lower`),
    KEY `idx_profiles_username_updated` (`username_lower`, `updated_at`)
);

CREATE TABLE IF NOT EXISTS `${tablePrefix}edges` (
    `player_low_uuid` BINARY(16) NOT NULL,
    `player_high_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_low_uuid`, `player_high_uuid`),
    KEY `idx_edges_high` (`player_high_uuid`)
);

CREATE TABLE IF NOT EXISTS `${tablePrefix}requests` (
    `sender_uuid` BINARY(16) NOT NULL,
    `target_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`sender_uuid`, `target_uuid`),
    KEY `idx_requests_target` (`target_uuid`, `created_at`),
    KEY `idx_requests_expires` (`expires_at`)
);

CREATE TABLE IF NOT EXISTS `${tablePrefix}blocks` (
    `blocker_uuid` BINARY(16) NOT NULL,
    `blocked_uuid` BINARY(16) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`blocker_uuid`, `blocked_uuid`),
    KEY `idx_blocks_blocked` (`blocked_uuid`)
);

CREATE TABLE IF NOT EXISTS `${tablePrefix}settings` (
    `player_uuid` BINARY(16) NOT NULL,
    `setting_key` VARCHAR(64) NOT NULL,
    `setting_value` VARCHAR(255) NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_uuid`, `setting_key`)
);

CREATE TABLE IF NOT EXISTS `${tablePrefix}offline_messages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_uuid` BINARY(16) NOT NULL,
    `target_uuid` BINARY(16) NOT NULL,
    `message` VARCHAR(512) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `delivered_at` TIMESTAMP NULL,
    `expires_at` TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_offline_target_pending` (`target_uuid`, `delivered_at`, `created_at`),
    KEY `idx_offline_expires` (`expires_at`)
);
