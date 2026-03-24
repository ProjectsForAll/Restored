package gg.drak.restored.database.dao;

import gg.drak.restored.Restored;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.database.Statements;
import gg.drak.restored.data.screens.items.StoredItem;
import host.plas.bou.gui.items.ItemData;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Data Access Object for Disk operations.
 */
@Getter
public class DiskDAO {
    private final MainOperator operator;
    
    public DiskDAO(MainOperator operator) {
        this.operator = operator;
    }
    
    /**
     * Insert or update a disk in the database.
     * @param identifier Disk UUID
     * @param driveId Drive block identifier (can be null)
     * @param capacity Disk capacity
     * @param items Disk contents
     */
    public void insert(String identifier, String driveId, Integer slot, BigInteger capacity, ConcurrentSkipListSet<StoredItem> items) {
        try {
            operator.ensureUsable();
            
            String statement = Statements.getStatement(Statements.StatementType.INSERT_DISK, operator.getConnectorSet());
            String itemsJson = serializeItems(items);

            operator.getMiddleware().queueOperation(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                    stmt.setString(2, driveId);
                    stmt.setObject(3, slot);
                    stmt.setString(4, capacity.toString());
                    stmt.setString(5, itemsJson);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for INSERT_DISK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to queue insert or update disk with identifier: " + identifier, e);
        }
    }

    /**
     * Delete a disk from the database.
     * @param identifier Disk UUID
     */
    public void delete(String identifier) {
        try {
            operator.ensureUsable();
            
            // Remove from cache immediately
            operator.getMiddleware().removeDiskFromCache(identifier);

            String statement = "DELETE FROM `" + operator.getConnectorSet().getTablePrefix() + "Disks` WHERE Identifier = ?;";

            operator.getMiddleware().queueOperation(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for DELETE_DISK statement", e);
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to queue delete disk with identifier: " + identifier, e);
        }
    }

    /**
     * Get all disks associated with a drive.
     * @param driveId Drive block identifier
     * @return List of DiskData
     */
    public java.util.List<DiskData> getByDriveId(String driveId) {
        try {
            operator.ensureUsable();

            String statement = Statements.getStatement(Statements.StatementType.GET_DISKS_BY_DRIVE, operator.getConnectorSet());

            java.util.List<DiskData> disks = new java.util.ArrayList<>();

            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, driveId);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for GET_DISKS_BY_DRIVE statement", e);
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    while (rs.next()) {
                        String identifier = rs.getString("Identifier");
                        int slot = rs.getInt("Slot");
                        BigInteger capacity = new BigInteger(rs.getString("Capacity"));
                        String itemsJson = rs.getString("Items");
                        ConcurrentSkipListSet<StoredItem> items = deserializeItems(itemsJson);

                        disks.add(new DiskData(identifier, driveId, slot, capacity, items));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_DISKS_BY_DRIVE result set", e);
                }
            });

            return disks;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get disks for drive: " + driveId, e);
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Get a disk by identifier.
     * @param identifier Disk UUID
     * @return Optional containing DiskData if found
     */
    public Optional<DiskData> getById(String identifier) {
        try {
            operator.ensureUsable();

            String statement = Statements.getStatement(Statements.StatementType.GET_DISK, operator.getConnectorSet());

            AtomicReference<Optional<DiskData>> ref = new AtomicReference<>(Optional.empty());

            operator.executeQuery(statement, stmt -> {
                try {
                    stmt.setString(1, identifier);
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to set values for GET_DISK statement", e);
                    throw new RuntimeException(e);
                }
            }, rs -> {
                try {
                    if (rs.next()) {
                        String driveId = rs.getString("DriveId");
                        int slot = rs.getInt("Slot");
                        BigInteger capacity = new BigInteger(rs.getString("Capacity"));
                        String itemsJson = rs.getString("Items");
                        ConcurrentSkipListSet<StoredItem> items = deserializeItems(itemsJson);

                        DiskData data = new DiskData(identifier, driveId, slot, capacity, items);
                        
                        ref.set(Optional.of(data));
                    }
                } catch (Exception e) {
                    Restored.getInstance().logSevere("Failed to read values from GET_DISK result set", e);
                }
            });

            return ref.get();
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get disk with identifier: " + identifier, e);
            return Optional.empty();
        }
    }
    
    /**
     * Serialize items to JSON string using Gson.
     * Format: [{"identifier":"uuid","amount":"123","itemData":"serialized"}]
     */
    private String serializeItems(ConcurrentSkipListSet<StoredItem> items) {
        try {
            if (items == null || items.isEmpty()) {
                return "[]";
            }

            com.google.gson.JsonArray array = new com.google.gson.JsonArray();

            for (StoredItem item : items) {
                ItemData itemData = item.toData();
                String data = itemData.getData();
                if (data == null || data.isEmpty() || data.equals("{}")) {
                    Restored.getInstance().logWarning("Skipping serialization of item with empty data: " + item.getIdentifier());
                    continue;
                }

                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("identifier", item.getIdentifier());
                obj.addProperty("amount", item.getAmount().toString());
                obj.addProperty("itemData", data);
                array.add(obj);
            }

            return array.toString();
        } catch (Exception e) {
            Restored.getInstance().logWarning("Failed to serialize disk items to JSON", e);
            return "[]";
        }
    }

    /**
     * Deserialize items from JSON string using Gson.
     */
    private ConcurrentSkipListSet<StoredItem> deserializeItems(String json) {
        ConcurrentSkipListSet<StoredItem> items = new ConcurrentSkipListSet<>();

        if (json == null || json.isEmpty() || json.trim().equals("[]")) {
            return items;
        }

        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();

            for (com.google.gson.JsonElement element : array) {
                try {
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    String identifier = obj.get("identifier").getAsString();
                    String amountStr = obj.get("amount").getAsString();
                    String itemDataStr = obj.get("itemData").getAsString();

                    BigInteger amount = new BigInteger(amountStr);
                    ItemData itemData = new ItemData(identifier, amount, itemDataStr);
                    StoredItem item = new StoredItem(itemData);
                    items.add(item);
                } catch (Exception e) {
                    Restored.getInstance().logWarning("Failed to parse item element: " + element, e);
                }
            }
        } catch (Exception e) {
            Restored.getInstance().logWarning("Failed to deserialize disk items from JSON: " + json, e);
        }

        return items;
    }
    
    @Getter
    @Setter
    public static class DiskData {
        private final String identifier;
        private final String driveId;
        private final int slot;
        private final BigInteger capacity;
        private final ConcurrentSkipListSet<StoredItem> items;
        
        public DiskData(String identifier, String driveId, int slot, BigInteger capacity, ConcurrentSkipListSet<StoredItem> items) {
            this.identifier = identifier;
            this.driveId = driveId;
            this.slot = slot;
            this.capacity = capacity;
            this.items = items != null ? items : new ConcurrentSkipListSet<>();
        }
    }
}
