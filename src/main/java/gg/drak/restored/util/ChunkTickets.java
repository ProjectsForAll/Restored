package gg.drak.restored.util;

import gg.drak.restored.Restored;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Reference-counted plugin chunk tickets.
 *
 * <p>Bukkit keeps at most one ticket per plugin per chunk, and {@code removePluginChunkTicket}
 * drops it outright. Every part of Restored that pins chunks (keep-loaded linked chests, remote
 * browsing leases) therefore goes through this class, so one holder releasing a chunk never
 * unpins it for another.
 *
 * <p>Chunks are loaded asynchronously before the ticket is added, so acquiring never stalls the
 * calling thread. A release that lands before that load finishes simply means the ticket is
 * never added.
 */
public final class ChunkTickets {

    private static final Object LOCK = new Object();
    private static final Map<String, Holder> HOLDERS = new HashMap<>();
    private static volatile boolean warnedUnsupported;

    private ChunkTickets() {
    }

    private static final class Holder {
        private final World world;
        private final int chunkX;
        private final int chunkZ;
        private int refs;
        private boolean ticketed;
        private CompletableFuture<Void> pending;

        private Holder(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private Location anchor() {
            return new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
        }
    }

    private static String id(String worldName, int chunkX, int chunkZ) {
        return worldName + ':' + chunkX + ':' + chunkZ;
    }

    /**
     * Adds one reference to the chunk and, on the first reference, loads it asynchronously and
     * pins it. The returned future completes once the chunk is loaded and pinned, or once the
     * attempt has failed; it never completes exceptionally.
     */
    public static CompletableFuture<Void> acquire(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        Holder holder;
        CompletableFuture<Void> done;
        synchronized (LOCK) {
            holder = HOLDERS.computeIfAbsent(id(world.getName(), chunkX, chunkZ), k -> new Holder(world, chunkX, chunkZ));
            holder.refs++;
            if (holder.ticketed) {
                return CompletableFuture.completedFuture(null);
            }
            if (holder.pending != null) {
                return holder.pending;
            }
            done = new CompletableFuture<>();
            holder.pending = done;
        }
        Holder target = holder;
        Location anchor = target.anchor();
        PlatformScheduler.loadChunk(anchor).whenComplete((ignored, error) ->
                PlatformScheduler.runAtLocation(anchor, () -> {
                    try {
                        addTicketIfStillWanted(target);
                    } finally {
                        done.complete(null);
                    }
                }));
        return done;
    }

    private static void addTicketIfStillWanted(Holder holder) {
        synchronized (LOCK) {
            holder.pending = null;
            // A holder that was fully released (and possibly replaced) while loading is stale.
            if (holder.refs <= 0 || holder.ticketed
                    || HOLDERS.get(id(holder.world.getName(), holder.chunkX, holder.chunkZ)) != holder) {
                return;
            }
            try {
                holder.world.addPluginChunkTicket(holder.chunkX, holder.chunkZ, Restored.getInstance());
                holder.ticketed = true;
            } catch (Throwable t) {
                if (!warnedUnsupported) {
                    warnedUnsupported = true;
                    Restored.getInstance().logWarning(
                            "Plugin chunk tickets are unavailable on this server; linked chests will "
                                    + "only be reachable while their chunks are loaded: " + t.getMessage());
                }
            }
        }
    }

    /** Removes one reference; the ticket is dropped when the last reference goes. */
    public static void release(World world, int chunkX, int chunkZ) {
        if (world != null) {
            release(world.getName(), chunkX, chunkZ);
        }
    }

    /** As {@link #release(World, int, int)}, usable after the world itself has unloaded. */
    public static void release(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) {
            return;
        }
        Holder holder;
        synchronized (LOCK) {
            String id = id(worldName, chunkX, chunkZ);
            holder = HOLDERS.get(id);
            if (holder == null) {
                return;
            }
            holder.refs--;
            if (holder.refs > 0) {
                return;
            }
            HOLDERS.remove(id);
            if (!holder.ticketed) {
                return;
            }
            holder.ticketed = false;
        }
        Holder target = holder;
        PlatformScheduler.runAtLocation(target.anchor(), () -> {
            try {
                target.world.removePluginChunkTicket(target.chunkX, target.chunkZ, Restored.getInstance());
            } catch (Throwable ignored) {
                // Nothing to undo when the server never accepted the ticket.
            }
        });
    }

    /** Drops every ticket this plugin holds. Used on disable. */
    public static void releaseAll() {
        synchronized (LOCK) {
            HOLDERS.clear();
        }
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            try {
                world.removePluginChunkTickets(Restored.getInstance());
            } catch (Throwable ignored) {
                // Unsupported on this platform; the server discards plugin tickets on disable.
            }
        }
    }
}
