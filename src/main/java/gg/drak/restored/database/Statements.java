package gg.drak.restored.database;

import host.plas.bou.sql.ConnectorSet;
import lombok.Getter;

/**
 * SQL statements for the single-chest network model.
 */
public final class Statements {

    private Statements() {
    }

    @Getter
    public enum MySQL {
        CREATE_DATABASE("CREATE DATABASE IF NOT EXISTS `%database%`;"),

        CREATE_TABLES(
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Networks` ( " +
                "Identifier VARCHAR(36) NOT NULL, " +
                "OwnerUuid VARCHAR(36) NOT NULL, " +
                "World VARCHAR(255) DEFAULT NULL, " +
                "X INTEGER DEFAULT NULL, " +
                "Y INTEGER DEFAULT NULL, " +
                "Z INTEGER DEFAULT NULL, " +
                "UpgradeCount INTEGER NOT NULL DEFAULT 0, " +
                "TotalOpens BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (Identifier) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkItems` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "ItemKey TEXT NOT NULL, " +
                "ItemData TEXT NOT NULL, " +
                "Amount BIGINT NOT NULL, " +
                "PRIMARY KEY (NetworkId, ItemKey(255)), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkPermissions` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "PlayerUuid VARCHAR(36) NOT NULL, " +
                "Role VARCHAR(32) NOT NULL, " +
                "PRIMARY KEY (NetworkId, PlayerUuid), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkOpenStats` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "PlayerUuid VARCHAR(36) NOT NULL, " +
                "Opens BIGINT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (NetworkId, PlayerUuid), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkAugments` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "AugmentType VARCHAR(32) NOT NULL, " +
                "PRIMARY KEY (NetworkId, AugmentType), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkLinkedChests` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "World VARCHAR(255) NOT NULL, " +
                "X INTEGER NOT NULL, " +
                "Y INTEGER NOT NULL, " +
                "Z INTEGER NOT NULL, " +
                "PRIMARY KEY (NetworkId, World(191), X, Y, Z), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; "
        ),

        INSERT_NETWORK("INSERT INTO `%table_prefix%Networks` (Identifier, OwnerUuid, World, X, Y, Z, UpgradeCount, TotalOpens) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "OwnerUuid = VALUES(OwnerUuid), World = VALUES(World), X = VALUES(X), Y = VALUES(Y), Z = VALUES(Z), " +
                "UpgradeCount = VALUES(UpgradeCount), TotalOpens = VALUES(TotalOpens);"),

        DELETE_NETWORK("DELETE FROM `%table_prefix%Networks` WHERE Identifier = ?;"),

        GET_NETWORK("SELECT * FROM `%table_prefix%Networks` WHERE Identifier = ?;"),

        GET_ALL_NETWORKS("SELECT * FROM `%table_prefix%Networks`;"),

        INSERT_NETWORK_ITEM("INSERT INTO `%table_prefix%NetworkItems` (NetworkId, ItemKey, ItemData, Amount) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE ItemData = VALUES(ItemData), Amount = VALUES(Amount);"),

        DELETE_NETWORK_ITEMS("DELETE FROM `%table_prefix%NetworkItems` WHERE NetworkId = ?;"),

        GET_NETWORK_ITEMS("SELECT * FROM `%table_prefix%NetworkItems` WHERE NetworkId = ?;"),

        INSERT_NETWORK_PERMISSION("INSERT INTO `%table_prefix%NetworkPermissions` (NetworkId, PlayerUuid, Role) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE Role = VALUES(Role);"),

        DELETE_NETWORK_PERMISSIONS("DELETE FROM `%table_prefix%NetworkPermissions` WHERE NetworkId = ?;"),

        GET_NETWORK_PERMISSIONS("SELECT * FROM `%table_prefix%NetworkPermissions` WHERE NetworkId = ?;"),

        INSERT_NETWORK_OPEN_STAT("INSERT INTO `%table_prefix%NetworkOpenStats` (NetworkId, PlayerUuid, Opens) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE Opens = VALUES(Opens);"),

        DELETE_NETWORK_OPEN_STATS("DELETE FROM `%table_prefix%NetworkOpenStats` WHERE NetworkId = ?;"),

        GET_NETWORK_OPEN_STATS("SELECT * FROM `%table_prefix%NetworkOpenStats` WHERE NetworkId = ?;"),

        INSERT_NETWORK_AUGMENT("INSERT INTO `%table_prefix%NetworkAugments` (NetworkId, AugmentType) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE AugmentType = VALUES(AugmentType);"),

        DELETE_NETWORK_AUGMENTS("DELETE FROM `%table_prefix%NetworkAugments` WHERE NetworkId = ?;"),

        GET_NETWORK_AUGMENTS("SELECT * FROM `%table_prefix%NetworkAugments` WHERE NetworkId = ?;"),

        INSERT_NETWORK_LINKED_CHEST("INSERT INTO `%table_prefix%NetworkLinkedChests` (NetworkId, World, X, Y, Z) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE World = VALUES(World);"),

        DELETE_NETWORK_LINKED_CHESTS("DELETE FROM `%table_prefix%NetworkLinkedChests` WHERE NetworkId = ?;"),

        GET_NETWORK_LINKED_CHESTS("SELECT * FROM `%table_prefix%NetworkLinkedChests` WHERE NetworkId = ?;"),
        ;

        private final String statement;

        MySQL(String statement) {
            this.statement = statement;
        }
    }

    @Getter
    public enum SQLite {
        CREATE_DATABASE(""),

        CREATE_TABLES(
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Networks` ( " +
                "Identifier TEXT NOT NULL, " +
                "OwnerUuid TEXT NOT NULL, " +
                "World TEXT DEFAULT NULL, " +
                "X INTEGER DEFAULT NULL, " +
                "Y INTEGER DEFAULT NULL, " +
                "Z INTEGER DEFAULT NULL, " +
                "UpgradeCount INTEGER NOT NULL DEFAULT 0, " +
                "TotalOpens INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (Identifier) " +
                ");; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkItems` ( " +
                "NetworkId TEXT NOT NULL, " +
                "ItemKey TEXT NOT NULL, " +
                "ItemData TEXT NOT NULL, " +
                "Amount INTEGER NOT NULL, " +
                "PRIMARY KEY (NetworkId, ItemKey), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkPermissions` ( " +
                "NetworkId TEXT NOT NULL, " +
                "PlayerUuid TEXT NOT NULL, " +
                "Role TEXT NOT NULL, " +
                "PRIMARY KEY (NetworkId, PlayerUuid), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkOpenStats` ( " +
                "NetworkId TEXT NOT NULL, " +
                "PlayerUuid TEXT NOT NULL, " +
                "Opens INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (NetworkId, PlayerUuid), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkAugments` ( " +
                "NetworkId TEXT NOT NULL, " +
                "AugmentType TEXT NOT NULL, " +
                "PRIMARY KEY (NetworkId, AugmentType), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +

                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkLinkedChests` ( " +
                "NetworkId TEXT NOT NULL, " +
                "World TEXT NOT NULL, " +
                "X INTEGER NOT NULL, " +
                "Y INTEGER NOT NULL, " +
                "Z INTEGER NOT NULL, " +
                "PRIMARY KEY (NetworkId, World, X, Y, Z), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; "
        ),

        INSERT_NETWORK("INSERT OR REPLACE INTO `%table_prefix%Networks` (Identifier, OwnerUuid, World, X, Y, Z, UpgradeCount, TotalOpens) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?);"),

        DELETE_NETWORK("DELETE FROM `%table_prefix%Networks` WHERE Identifier = ?;"),

        GET_NETWORK("SELECT * FROM `%table_prefix%Networks` WHERE Identifier = ?;"),

        GET_ALL_NETWORKS("SELECT * FROM `%table_prefix%Networks`;"),

        INSERT_NETWORK_ITEM("INSERT OR REPLACE INTO `%table_prefix%NetworkItems` (NetworkId, ItemKey, ItemData, Amount) VALUES (?, ?, ?, ?);"),

        DELETE_NETWORK_ITEMS("DELETE FROM `%table_prefix%NetworkItems` WHERE NetworkId = ?;"),

        GET_NETWORK_ITEMS("SELECT * FROM `%table_prefix%NetworkItems` WHERE NetworkId = ?;"),

        INSERT_NETWORK_PERMISSION("INSERT OR REPLACE INTO `%table_prefix%NetworkPermissions` (NetworkId, PlayerUuid, Role) VALUES (?, ?, ?);"),

        DELETE_NETWORK_PERMISSIONS("DELETE FROM `%table_prefix%NetworkPermissions` WHERE NetworkId = ?;"),

        GET_NETWORK_PERMISSIONS("SELECT * FROM `%table_prefix%NetworkPermissions` WHERE NetworkId = ?;"),

        INSERT_NETWORK_OPEN_STAT("INSERT OR REPLACE INTO `%table_prefix%NetworkOpenStats` (NetworkId, PlayerUuid, Opens) VALUES (?, ?, ?);"),

        DELETE_NETWORK_OPEN_STATS("DELETE FROM `%table_prefix%NetworkOpenStats` WHERE NetworkId = ?;"),

        GET_NETWORK_OPEN_STATS("SELECT * FROM `%table_prefix%NetworkOpenStats` WHERE NetworkId = ?;"),

        INSERT_NETWORK_AUGMENT("INSERT OR REPLACE INTO `%table_prefix%NetworkAugments` (NetworkId, AugmentType) VALUES (?, ?);"),

        DELETE_NETWORK_AUGMENTS("DELETE FROM `%table_prefix%NetworkAugments` WHERE NetworkId = ?;"),

        GET_NETWORK_AUGMENTS("SELECT * FROM `%table_prefix%NetworkAugments` WHERE NetworkId = ?;"),

        INSERT_NETWORK_LINKED_CHEST("INSERT OR REPLACE INTO `%table_prefix%NetworkLinkedChests` (NetworkId, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);"),

        DELETE_NETWORK_LINKED_CHESTS("DELETE FROM `%table_prefix%NetworkLinkedChests` WHERE NetworkId = ?;"),

        GET_NETWORK_LINKED_CHESTS("SELECT * FROM `%table_prefix%NetworkLinkedChests` WHERE NetworkId = ?;"),
        ;

        private final String statement;

        SQLite(String statement) {
            this.statement = statement;
        }
    }

    public enum StatementType {
        CREATE_DATABASE,
        CREATE_TABLES,
        INSERT_NETWORK,
        DELETE_NETWORK,
        GET_NETWORK,
        GET_ALL_NETWORKS,
        INSERT_NETWORK_ITEM,
        DELETE_NETWORK_ITEMS,
        GET_NETWORK_ITEMS,
        INSERT_NETWORK_PERMISSION,
        DELETE_NETWORK_PERMISSIONS,
        GET_NETWORK_PERMISSIONS,
        INSERT_NETWORK_OPEN_STAT,
        DELETE_NETWORK_OPEN_STATS,
        GET_NETWORK_OPEN_STATS,
        INSERT_NETWORK_AUGMENT,
        DELETE_NETWORK_AUGMENTS,
        GET_NETWORK_AUGMENTS,
        INSERT_NETWORK_LINKED_CHEST,
        DELETE_NETWORK_LINKED_CHESTS,
        GET_NETWORK_LINKED_CHESTS,
    }

    public static String getStatement(StatementType type, ConnectorSet connectorSet) {
        return switch (connectorSet.getType()) {
            case MYSQL -> MySQL.valueOf(type.name()).getStatement()
                    .replace("%database%", connectorSet.getDatabase())
                    .replace("%table_prefix%", connectorSet.getTablePrefix());
            case SQLITE -> SQLite.valueOf(type.name()).getStatement()
                    .replace("%table_prefix%", connectorSet.getTablePrefix());
            default -> "";
        };
    }
}
