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
     * @param capacity Disk capacity
     * @param items Disk contents
     * @throws SQLException if database operation fails
     */
    public void insert(String identifier, BigInteger capacity, ConcurrentSkipListSet<StoredItem> items) throws SQLException {
        operator.ensureUsable();
        
        String statement = Statements.getStatement(Statements.StatementType.INSERT_DISK, operator.getConnectorSet());
        String itemsJson = serializeItems(items);
        
        operator.execute(statement, stmt -> {
            try {
                stmt.setString(1, identifier);
                stmt.setString(2, capacity.toString());
                stmt.setString(3, itemsJson);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to set values for INSERT_DISK statement", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Get a disk by identifier.
     * @param identifier Disk UUID
     * @return Optional containing DiskData if found
     * @throws SQLException if database operation fails
     */
    public Optional<DiskData> getById(String identifier) throws SQLException {
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
                    BigInteger capacity = new BigInteger(rs.getString("Capacity"));
                    String itemsJson = rs.getString("Items");
                    ConcurrentSkipListSet<StoredItem> items = deserializeItems(itemsJson);
                    
                    DiskData data = new DiskData(identifier, capacity, items);
                    ref.set(Optional.of(data));
                }
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to read values from GET_DISK result set", e);
            }
        });
        
        return ref.get();
    }
    
    /**
     * Serialize items to JSON string.
     * Format: [{"identifier":"uuid","amount":"123","itemData":"serialized"}]
     */
    private String serializeItems(ConcurrentSkipListSet<StoredItem> items) {
        operator.ensureUsable();

        if (items == null || items.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (StoredItem item : items) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            ItemData itemData = item.toData();
            String itemDataStr = escapeJson(itemData.getData());
            String identifier = escapeJson(item.getIdentifier());
            String amount = item.getAmount().toString();
            
            sb.append("{")
              .append("\"identifier\":\"").append(identifier).append("\",")
              .append("\"amount\":\"").append(amount).append("\",")
              .append("\"itemData\":\"").append(itemDataStr).append("\"")
              .append("}");
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Deserialize items from JSON string.
     */
    private ConcurrentSkipListSet<StoredItem> deserializeItems(String json) {
        operator.ensureUsable();

        ConcurrentSkipListSet<StoredItem> items = new ConcurrentSkipListSet<>();
        
        if (json == null || json.isEmpty() || json.trim().equals("[]")) {
            return items;
        }
        
        try {
            // Simple JSON parsing - find each object in the array
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) {
                Restored.getInstance().logWarning("Invalid JSON format for disk items: " + json);
                return items;
            }
            
            String content = json.substring(1, json.length() - 1).trim();
            if (content.isEmpty()) {
                return items;
            }
            
            // Parse objects by finding balanced braces
            int depth = 0;
            int start = -1;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') {
                    if (depth == 0) {
                        start = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        String objStr = content.substring(start, i + 1);
                        parseItemObject(objStr, items);
                        start = -1;
                    }
                }
            }
        } catch (Exception e) {
            Restored.getInstance().logWarning("Failed to deserialize disk items from JSON: " + json, e);
        }
        
        return items;
    }
    
    /**
     * Parse a single JSON object and add it to the items set.
     */
    private void parseItemObject(String objStr, ConcurrentSkipListSet<StoredItem> items) {
        operator.ensureUsable();

        try {
            String identifier = extractJsonValue(objStr, "identifier");
            String amountStr = extractJsonValue(objStr, "amount");
            String itemDataStr = extractJsonValue(objStr, "itemData");
            
            if (identifier != null && amountStr != null && itemDataStr != null) {
                BigInteger amount = new BigInteger(amountStr);
                ItemData itemData = new ItemData(identifier, amount, itemDataStr);
                StoredItem item = new StoredItem(itemData);
                items.add(item);
            }
        } catch (Exception e) {
            Restored.getInstance().logWarning("Failed to parse item object: " + objStr, e);
        }
    }
    
    /**
     * Extract a JSON value from a JSON object string.
     */
    private String extractJsonValue(String json, String key) {
        operator.ensureUsable();

        try {
            String searchKey = "\"" + key + "\":\"";
            int startIdx = json.indexOf(searchKey);
            if (startIdx == -1) {
                return null;
            }
            
            startIdx += searchKey.length();
            int endIdx = json.indexOf("\"", startIdx);
            if (endIdx == -1) {
                return null;
            }
            
            return unescapeJson(json.substring(startIdx, endIdx));
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Escape JSON special characters.
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Unescape JSON special characters.
     */
    private String unescapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }
    
    @Getter
    @Setter
    public static class DiskData {
        private final String identifier;
        private final BigInteger capacity;
        private final ConcurrentSkipListSet<StoredItem> items;
        
        public DiskData(String identifier, BigInteger capacity, ConcurrentSkipListSet<StoredItem> items) {
            this.identifier = identifier;
            this.capacity = capacity;
            this.items = items != null ? items : new ConcurrentSkipListSet<>();
        }
    }
}
