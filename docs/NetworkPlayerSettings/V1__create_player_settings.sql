CREATE TABLE IF NOT EXISTS `${tablePrefix}player_settings` (
    `player_uuid` BINARY(16) NOT NULL,
    `setting_key` VARCHAR(64) NOT NULL,
    `setting_value` VARCHAR(255) NOT NULL,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`player_uuid`, `setting_key`)
);
