package gg.drak.restored.util;

import gg.drak.restored.Restored;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduling that works on both Paper and Folia.
 *
 * <p>Folia is detected once by class lookup; its region scheduler is invoked reflectively so this
 * class still compiles and runs against a plain Paper server, where the legacy main-thread
 * scheduler is used instead.
 */
public final class PlatformScheduler {

    private static final boolean FOLIA;
    private static Method regionSchedulerExecute;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
        if (FOLIA) {
            try {
                Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                regionSchedulerExecute = scheduler.getClass()
                        .getMethod("execute", org.bukkit.plugin.Plugin.class, Location.class, Runnable.class);
            } catch (ReflectiveOperationException e) {
                regionSchedulerExecute = null;
            }
        }
    }

    private PlatformScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs {@code task} on the thread that owns {@code location}: the region thread on Folia, the
     * main thread on Paper. Runs inline when the caller already owns the location.
     */
    public static void runAtLocation(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (FOLIA && regionSchedulerExecute != null) {
            try {
                Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                regionSchedulerExecute.invoke(scheduler, Restored.getInstance(), location, task);
                return;
            } catch (ReflectiveOperationException e) {
                Restored.getInstance().logWarning("Region scheduling failed, falling back: " + e.getMessage());
            }
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(Restored.getInstance(), task);
        }
    }

    /**
     * Asynchronously loads the chunk containing {@code location} without blocking the calling
     * thread. The future completes on an unspecified thread; callers must hop back to a
     * location-owning thread (see {@link #runAtLocation}) before touching blocks.
     */
    public static CompletableFuture<Void> loadChunk(Location location) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return world.getChunkAtAsync(chunkX, chunkZ).thenApply(chunk -> null);
        } catch (Throwable t) {
            // Very old servers without getChunkAtAsync: give up rather than force a sync load.
            return CompletableFuture.completedFuture(null);
        }
    }
}
