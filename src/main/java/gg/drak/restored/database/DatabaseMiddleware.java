package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.database.dao.DiskDAO;
import gg.drak.restored.database.dao.NetworkBlockDAO;
import gg.drak.restored.database.dao.NetworkDAO;
import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Middleware for caching database operations and executing them in async batches.
 * Also acts as a write-through cache for immediate data consistency.
 */
public class DatabaseMiddleware {
    private final MainOperator operator;
    private final ConcurrentLinkedQueue<DatabaseOperation> operationQueue;
    private final int batchSize = 50;
    private final long flushIntervalTicks = 20L; // 1 second

    // High-level object caches (Source of Truth)
    private final ConcurrentHashMap<String, StorageDisk> diskCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkBlock> blockCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Network> networkCache = new ConcurrentHashMap<>();

    public DatabaseMiddleware(MainOperator operator) {
        this.operator = operator;
        this.operationQueue = new ConcurrentLinkedQueue<>();
        startFlushTask();
    }

    public void queueOperation(String statement, Consumer<java.sql.PreparedStatement> parameterSetter) {
        operationQueue.add(new DatabaseOperation(statement, parameterSetter));
    }

    public void cacheDisk(StorageDisk disk) {
        diskCache.put(disk.getIdentifier(), disk);
    }

    public void cacheBlock(NetworkBlock block) {
        blockCache.put(block.getIdentifier(), block);
    }

    public void cacheNetwork(Network network) {
        networkCache.put(network.getIdentifier(), network);
    }

    public void removeDiskFromCache(String identifier) {
        diskCache.remove(identifier);
    }

    public void removeBlockFromCache(String identifier) {
        blockCache.remove(identifier);
    }

    public void removeNetworkFromCache(String identifier) {
        networkCache.remove(identifier);
    }

    public Optional<StorageDisk> getCachedDisk(String identifier) {
        return Optional.ofNullable(diskCache.get(identifier));
    }

    public Optional<NetworkBlock> getCachedBlock(String identifier) {
        return Optional.ofNullable(blockCache.get(identifier));
    }

    public Optional<Network> getCachedNetwork(String identifier) {
        return Optional.ofNullable(networkCache.get(identifier));
    }

    public List<Network> getAllCachedNetworks() {
        return new ArrayList<>(networkCache.values());
    }

    public List<StorageDisk> getAllCachedDisks() {
        return new ArrayList<>(diskCache.values());
    }

    private void startFlushTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                flush();
            }
        }.runTaskTimerAsynchronously(Restored.getInstance(), flushIntervalTicks, flushIntervalTicks);
    }

    public void flush() {
        if (operationQueue.isEmpty()) return;

        List<DatabaseOperation> batch = new ArrayList<>();
        DatabaseOperation op;
        while (batch.size() < batchSize && (op = operationQueue.poll()) != null) {
            batch.add(op);
        }

        if (batch.isEmpty()) return;

        // Group by statement to maintain execution order for the same statement,
        // but we must process statements in the order they appeared in the batch
        // to respect foreign key constraints if different statements are mixed.
        // However, JDBC batching is most efficient when we group by statement.
        
        // To be safe with foreign keys, we'll process the batch in order, 
        // but still use JDBC batching for consecutive identical statements.
        
        try {
            operator.ensureUsable();
            operator.getConnection().setAutoCommit(false);
            
            String currentStatement = null;
            java.sql.PreparedStatement pstmt = null;
            
            try {
                for (DatabaseOperation operation : batch) {
                    if (currentStatement == null || !currentStatement.equals(operation.getStatement())) {
                        // Execute previous batch if it exists
                        if (pstmt != null) {
                            pstmt.executeBatch();
                            pstmt.close();
                        }
                        
                        currentStatement = operation.getStatement();
                        pstmt = operator.getConnection().prepareStatement(currentStatement);
                    }
                    
                    operation.getParameterSetter().accept(pstmt);
                    pstmt.addBatch();
                }
                
                if (pstmt != null) {
                    pstmt.executeBatch();
                    pstmt.close();
                }
                
                operator.getConnection().commit();
            } catch (Exception e) {
                if (pstmt != null) pstmt.close();
                operator.getConnection().rollback();
                Restored.getInstance().logSevere("Failed to execute JDBC batch", e);
            } finally {
                operator.getConnection().setAutoCommit(true);
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to manage connection for batched operations", e);
        }
        
        // If there's more in the queue, schedule another flush immediately
        if (!operationQueue.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    flush();
                }
            }.runTaskAsynchronously(Restored.getInstance());
        }
    }

    @Getter
    private static class DatabaseOperation {
        private final String statement;
        private final Consumer<java.sql.PreparedStatement> parameterSetter;

        public DatabaseOperation(String statement, Consumer<java.sql.PreparedStatement> parameterSetter) {
            this.statement = statement;
            this.parameterSetter = parameterSetter;
        }
    }
}
