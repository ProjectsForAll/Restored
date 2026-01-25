package gg.drak.restored.database;

import host.plas.bou.sql.ConnectorSet;
import lombok.Getter;

/**
 * SQL statements for database operations.
 * Supports both MySQL and SQLite database types.
 */
public class Statements {
    
    @Getter
    public enum MySQL {
        CREATE_DATABASE("CREATE DATABASE IF NOT EXISTS `%database%`;"),
        
        CREATE_TABLES(
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Networks` ( " +
                "Identifier VARCHAR(36) NOT NULL, " +
                "OwnerUuid VARCHAR(36) NOT NULL, " +
                "PRIMARY KEY (Identifier) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkBlocks` ( " +
                "Identifier VARCHAR(255) NOT NULL, " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "BlockType VARCHAR(50) NOT NULL, " +
                "Data TEXT, " +
                "PRIMARY KEY (Identifier), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Disks` ( " +
                "Identifier VARCHAR(36) NOT NULL, " +
                "DriveId VARCHAR(255) DEFAULT NULL, " +
                "Slot INTEGER, " +
                "Capacity TEXT NOT NULL, " +
                "Items TEXT, " +
                "PRIMARY KEY (Identifier) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Permissions` ( " +
                "NetworkId VARCHAR(36) NOT NULL, " +
                "PlayerUuid VARCHAR(36) NOT NULL, " +
                "PermissionNode VARCHAR(100) NOT NULL, " +
                "Value BOOLEAN NOT NULL DEFAULT TRUE, " +
                "PRIMARY KEY (NetworkId, PlayerUuid, PermissionNode), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Filters` ( " +
                "PlayerUuid VARCHAR(36) NOT NULL, " +
                "Filter TEXT, " +
                "PRIMARY KEY (PlayerUuid) " +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;; "
        ),
        
        INSERT_NETWORK("INSERT INTO `%table_prefix%Networks` (Identifier, OwnerUuid) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE OwnerUuid = VALUES(OwnerUuid);"),
        
        DELETE_NETWORK("DELETE FROM `%table_prefix%Networks` WHERE Identifier = ?;"),
        
        GET_NETWORK("SELECT * FROM `%table_prefix%Networks` WHERE Identifier = ?;"),
        
        INSERT_NETWORK_BLOCK("INSERT INTO `%table_prefix%NetworkBlocks` (Identifier, NetworkId, BlockType, Data) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE NetworkId = VALUES(NetworkId), BlockType = VALUES(BlockType), Data = VALUES(Data);"),
        
        DELETE_NETWORK_BLOCK("DELETE FROM `%table_prefix%NetworkBlocks` WHERE Identifier = ?;"),
        
        DELETE_NETWORK_BLOCKS_BY_NETWORK("DELETE FROM `%table_prefix%NetworkBlocks` WHERE NetworkId = ?;"),
        
        GET_NETWORK_BLOCKS_BY_NETWORK("SELECT * FROM `%table_prefix%NetworkBlocks` WHERE NetworkId = ?;"),
        GET_NETWORK_BLOCK("SELECT * FROM `%table_prefix%NetworkBlocks` WHERE Identifier = ?;"),
        
        INSERT_DISK("INSERT INTO `%table_prefix%Disks` (Identifier, DriveId, Slot, Capacity, Items) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE DriveId = VALUES(DriveId), Slot = VALUES(Slot), Capacity = VALUES(Capacity), Items = VALUES(Items);"),
        
        GET_DISK("SELECT * FROM `%table_prefix%Disks` WHERE Identifier = ?;"),
        GET_DISKS_BY_DRIVE("SELECT * FROM `%table_prefix%Disks` WHERE DriveId = ?;"),
        
        INSERT_PERMISSION("INSERT INTO `%table_prefix%Permissions` (NetworkId, PlayerUuid, PermissionNode, Value) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE Value = VALUES(Value);"),
        
        DELETE_PERMISSIONS_BY_NETWORK("DELETE FROM `%table_prefix%Permissions` WHERE NetworkId = ?;"),
        
        GET_PERMISSIONS_BY_NETWORK("SELECT * FROM `%table_prefix%Permissions` WHERE NetworkId = ?;"),
        
        SET_FILTER("INSERT INTO `%table_prefix%Filters` (PlayerUuid, Filter) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE Filter = VALUES(Filter);"),
        
        GET_FILTER("SELECT * FROM `%table_prefix%Filters` WHERE PlayerUuid = ?;"),
        
        CLEAR_FILTER("DELETE FROM `%table_prefix%Filters` WHERE PlayerUuid = ?;"),
        GET_ALL_NETWORKS("SELECT * FROM `%table_prefix%Networks`;"),
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
                "PRIMARY KEY (Identifier) " +
                ");; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%NetworkBlocks` ( " +
                "Identifier TEXT NOT NULL, " +
                "NetworkId TEXT NOT NULL, " +
                "BlockType TEXT NOT NULL, " +
                "Data TEXT, " +
                "PRIMARY KEY (Identifier), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Disks` ( " +
                "Identifier TEXT NOT NULL, " +
                "DriveId TEXT DEFAULT NULL, " +
                "Slot INTEGER, " +
                "Capacity TEXT NOT NULL, " +
                "Items TEXT, " +
                "PRIMARY KEY (Identifier) " +
                ");; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Permissions` ( " +
                "NetworkId TEXT NOT NULL, " +
                "PlayerUuid TEXT NOT NULL, " +
                "PermissionNode TEXT NOT NULL, " +
                "Value INTEGER NOT NULL DEFAULT 1, " +
                "PRIMARY KEY (NetworkId, PlayerUuid, PermissionNode), " +
                "FOREIGN KEY (NetworkId) REFERENCES `%table_prefix%Networks`(Identifier) ON DELETE CASCADE " +
                ");; " +
                
                "CREATE TABLE IF NOT EXISTS `%table_prefix%Filters` ( " +
                "PlayerUuid TEXT NOT NULL, " +
                "Filter TEXT, " +
                "PRIMARY KEY (PlayerUuid) " +
                ");; "
        ),
        
        INSERT_NETWORK("INSERT OR REPLACE INTO `%table_prefix%Networks` (Identifier, OwnerUuid) VALUES (?, ?);"),
        
        DELETE_NETWORK("DELETE FROM `%table_prefix%Networks` WHERE Identifier = ?;"),
        
        GET_NETWORK("SELECT * FROM `%table_prefix%Networks` WHERE Identifier = ?;"),
        
        INSERT_NETWORK_BLOCK("INSERT OR REPLACE INTO `%table_prefix%NetworkBlocks` (Identifier, NetworkId, BlockType) VALUES (?, ?, ?);"),
        
        DELETE_NETWORK_BLOCK("DELETE FROM `%table_prefix%NetworkBlocks` WHERE Identifier = ?;"),
        
        DELETE_NETWORK_BLOCKS_BY_NETWORK("DELETE FROM `%table_prefix%NetworkBlocks` WHERE NetworkId = ?;"),
        
        GET_NETWORK_BLOCKS_BY_NETWORK("SELECT * FROM `%table_prefix%NetworkBlocks` WHERE NetworkId = ?;"),
        GET_NETWORK_BLOCK("SELECT * FROM `%table_prefix%NetworkBlocks` WHERE Identifier = ?;"),
        
        INSERT_DISK("INSERT OR REPLACE INTO `%table_prefix%Disks` (Identifier, DriveId, Slot, Capacity, Items) VALUES (?, ?, ?, ?, ?);"),
        
        GET_DISK("SELECT * FROM `%table_prefix%Disks` WHERE Identifier = ?;"),
        GET_DISKS_BY_DRIVE("SELECT * FROM `%table_prefix%Disks` WHERE DriveId = ?;"),
        
        INSERT_PERMISSION("INSERT OR REPLACE INTO `%table_prefix%Permissions` (NetworkId, PlayerUuid, PermissionNode, Value) VALUES (?, ?, ?, ?);"),
        
        DELETE_PERMISSIONS_BY_NETWORK("DELETE FROM `%table_prefix%Permissions` WHERE NetworkId = ?;"),
        
        GET_PERMISSIONS_BY_NETWORK("SELECT * FROM `%table_prefix%Permissions` WHERE NetworkId = ?;"),
        
        SET_FILTER("INSERT OR REPLACE INTO `%table_prefix%Filters` (PlayerUuid, Filter) VALUES (?, ?);"),
        
        GET_FILTER("SELECT * FROM `%table_prefix%Filters` WHERE PlayerUuid = ?;"),
        
        CLEAR_FILTER("DELETE FROM `%table_prefix%Filters` WHERE PlayerUuid = ?;"),
        GET_ALL_NETWORKS("SELECT * FROM `%table_prefix%Networks`;"),
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
        INSERT_NETWORK_BLOCK,
        DELETE_NETWORK_BLOCK,
        DELETE_NETWORK_BLOCKS_BY_NETWORK,
        GET_NETWORK_BLOCKS_BY_NETWORK,
        GET_NETWORK_BLOCK,
        INSERT_DISK,
        GET_DISK,
        GET_DISKS_BY_DRIVE,
        INSERT_PERMISSION,
        DELETE_PERMISSIONS_BY_NETWORK,
        GET_PERMISSIONS_BY_NETWORK,
        SET_FILTER,
        GET_FILTER,
        CLEAR_FILTER,
        GET_ALL_NETWORKS,
        ;
    }

    public static String getStatement(StatementType type, ConnectorSet connectorSet) {
        switch (connectorSet.getType()) {
            case MYSQL:
                return MySQL.valueOf(type.name()).getStatement()
                        .replace("%database%", connectorSet.getDatabase())
                        .replace("%table_prefix%", connectorSet.getTablePrefix());
            case SQLITE:
                return SQLite.valueOf(type.name()).getStatement()
                        .replace("%table_prefix%", connectorSet.getTablePrefix());
            default:
                return "";
        }
    }
}
