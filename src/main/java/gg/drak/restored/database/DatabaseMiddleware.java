package gg.drak.restored.database;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * In-memory network cache plus coalesced async persistence.
 * Mutations update the cache immediately; DB writes are flushed off the main thread.
 */
public class DatabaseMiddleware {
    private final MainOperator operator;
    private final ConcurrentLinkedQueue<DatabaseOperation> operationQueue;
    private final ConcurrentHashMap<UUID, NetworkSnapshot> pendingSaves = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> pendingDeletes = new ConcurrentLinkedQueue<>();
    private final Object flushLock = new Object();

    private final int batchSize = 50;
    private final long flushIntervalTicks = 20L;

    private final ConcurrentHashMap<String, Network> networkCache = new ConcurrentHashMap<>();

    public DatabaseMiddleware(MainOperator operator) {
        this.operator = operator;
        this.operationQueue = new ConcurrentLinkedQueue<>();
        startFlushTask();
    }

    public void queueOperation(String statement, Consumer<java.sql.PreparedStatement> parameterSetter) {
        operationQueue.add(new DatabaseOperation(statement, parameterSetter));
    }

    /**
     * Capture a snapshot on the calling thread and queue it for async DB write.
     * Newer snapshots for the same network replace older pending ones.
     */
    public void queueNetworkSave(Network network) {
        cacheNetwork(network);
        if (!network.isDirty()) {
            return;
        }
        NetworkSnapshot snapshot = NetworkSnapshot.capture(network);
        network.getDirty().set(false);
        pendingSaves.put(network.getIdentifier(), snapshot);
    }

    public void queueNetworkDelete(Network network) {
        UUID id = network.getIdentifier();
        pendingSaves.remove(id);
        removeNetworkFromCache(network.getIdentifierString());
        pendingDeletes.add(network.getIdentifierString());
    }

    public void cacheNetwork(Network network) {
        networkCache.put(network.getIdentifierString(), network);
    }

    public void removeNetworkFromCache(String identifier) {
        networkCache.remove(identifier);
    }

    public Optional<Network> getCachedNetwork(String identifier) {
        return Optional.ofNullable(networkCache.get(identifier));
    }

    public List<Network> getAllCachedNetworks() {
        return new ArrayList<>(networkCache.values());
    }

    private void startFlushTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                flush();
            }
        }.runTaskTimerAsynchronously(Restored.getInstance(), flushIntervalTicks, flushIntervalTicks);
    }

    /**
     * Persist all pending network saves/deletes and any queued SQL ops.
     * Safe to call from async workers; also used on shutdown.
     */
    public void flush() {
        synchronized (flushLock) {
            flushPendingDeletes();
            flushPendingSaves();
            flushOperationQueue();
        }
    }

    private void flushPendingDeletes() {
        String id;
        while ((id = pendingDeletes.poll()) != null) {
            try {
                operator.deleteNetworkSync(id);
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to delete network " + id + " asynchronously", e);
            }
        }
    }

    private void flushPendingSaves() {
        if (pendingSaves.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, NetworkSnapshot>> batch = new ArrayList<>(pendingSaves.entrySet());
        for (Map.Entry<UUID, NetworkSnapshot> entry : batch) {
            if (!pendingSaves.remove(entry.getKey(), entry.getValue())) {
                // Replaced by a newer snapshot while iterating; skip this stale one.
                continue;
            }
            try {
                operator.persistSnapshot(entry.getValue());
            } catch (Exception e) {
                Restored.getInstance().logSevere("Failed to save network " + entry.getKey() + " asynchronously", e);
                // Re-queue so a later flush can retry.
                pendingSaves.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    private void flushOperationQueue() {
        if (operationQueue.isEmpty()) {
            return;
        }

        List<DatabaseOperation> batch = new ArrayList<>();
        DatabaseOperation op;
        while (batch.size() < batchSize && (op = operationQueue.poll()) != null) {
            batch.add(op);
        }

        if (batch.isEmpty()) {
            return;
        }

        boolean batchRequeued = false;
        try {
            operator.ensureUsable();
            operator.getConnection().setAutoCommit(false);

            String currentStatement = null;
            java.sql.PreparedStatement pstmt = null;

            try {
                for (DatabaseOperation operation : batch) {
                    if (currentStatement == null || !currentStatement.equals(operation.getStatement())) {
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
                try {
                    if (pstmt != null) {
                        pstmt.close();
                    }
                } catch (Exception closeError) {
                    e.addSuppressed(closeError);
                }
                try {
                    operator.getConnection().rollback();
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                Restored.getInstance().logSevere("Failed to execute JDBC batch", e);
                // The batch was removed from the queue before execution. Put it back so a
                // transient database failure does not silently discard writes.
                requeueBatch(batch);
                batchRequeued = true;
            } finally {
                operator.getConnection().setAutoCommit(true);
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to manage connection for batched operations", e);
            if (!batchRequeued) {
                requeueBatch(batch);
            }
        }

        if (!operationQueue.isEmpty()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    flush();
                }
            }.runTaskAsynchronously(Restored.getInstance());
        }
    }

    private void requeueBatch(List<DatabaseOperation> batch) {
        // Preserve the order of operations within the failed batch. The queue may contain
        // newer work already, but reversing a batch can make dependent writes inconsistent.
        for (DatabaseOperation operation : batch) {
            operationQueue.add(operation);
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
