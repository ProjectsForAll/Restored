package gg.drak.restored.util;

import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.StoredStack;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory map of linked storage block → the items it holds.
 *
 * <p>Every read of a network's linked chests (GUI listings, amount checks, augment scans) is
 * served from here instead of walking tile entities and hashing every slot. A chest's snapshot
 * is rebuilt from its live inventory only when it has been marked dirty (a player used it, a
 * hopper moved items, the network wrote to it, its chunk reloaded) or when it is older than
 * {@link #MAX_AGE_MILLIS}, which bounds staleness from changes no event reports.
 *
 * <p>A snapshot taken as a chunk unloads stays valid for as long as the chunk is unloaded,
 * because nothing can change a chest in an unloaded chunk. That is what lets a network list
 * the contents of chests that are not currently loaded.
 *
 * <p>Snapshots are read-only views for display and planning. Inserts and extractions always
 * act on the live inventory; see {@link LinkedChestStorage}.
 */
public final class LinkedChestCache {

    static final long MAX_AGE_MILLIS = 30_000L;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> BY_CHUNK = new ConcurrentHashMap<>();
    private static volatile boolean keepChunksLoaded;

    private LinkedChestCache() {
    }

    /**
     * Immutable contents summary of one linked chest (or double chest).
     *
     * @param totals     item key → stack whose template is a private copy; callers must copy
     *                   before handing a stack onward, never mutate these.
     * @param partialRoom item key → free space left in non-full stacks of that item.
     */
    public record Snapshot(
            Map<String, StoredStack> totals,
            Map<String, Long> partialRoom,
            int emptySlots,
            long totalItems,
            int blockCount,
            long takenAt
    ) {
        public long amount(String itemKey) {
            StoredStack stack = totals.get(itemKey);
            return stack == null ? 0 : stack.getAmount();
        }

        public boolean hasRoomFor(String itemKey) {
            return emptySlots > 0 || partialRoom.getOrDefault(itemKey, 0L) > 0;
        }
    }

    static final class Entry {
        private final String key;
        private final String world;
        private final int chunkX;
        private final int chunkZ;
        private int refs;
        private boolean ticketHeld;
        private String partnerChunk;
        private volatile Snapshot snapshot;
        private volatile boolean dirty = true;
        /** Per-slot item copies and their keys, so unchanged slots are never re-hashed. */
        private ItemStack[] slotItems = new ItemStack[0];
        private String[] slotKeys = new String[0];

        private Entry(String key, String world, int x, int z) {
            this.key = key;
            this.world = world;
            this.chunkX = x >> 4;
            this.chunkZ = z >> 4;
        }

        private String chunkId() {
            return LinkedChestCache.chunkId(world, chunkX, chunkZ);
        }

        /**
         * Item key for {@code stack} sitting in {@code slot}, reusing the previous hash when the
         * slot still holds a similar item.
         */
        synchronized String keyAt(int slot, ItemStack stack) {
            if (slot >= slotItems.length) {
                int size = Math.max(slot + 1, 54);
                slotItems = java.util.Arrays.copyOf(slotItems, size);
                slotKeys = java.util.Arrays.copyOf(slotKeys, size);
            }
            ItemStack previous = slotItems[slot];
            if (previous != null && previous.isSimilar(stack)) {
                return slotKeys[slot];
            }
            String itemKey = StoredStack.itemKey(stack);
            // Inventory contents are live mirrors of the server's stacks; keep a private copy
            // or the comparison above would always match the current contents.
            slotItems[slot] = stack.clone();
            slotKeys[slot] = itemKey;
            return itemKey;
        }
    }

    private static String chunkId(String world, int chunkX, int chunkZ) {
        return world + ':' + chunkX + ':' + chunkZ;
    }

    /** Whether linked chests' chunks are pinned while linked. Read once at startup. */
    public static void setKeepChunksLoaded(boolean keep) {
        keepChunksLoaded = keep;
    }

    public static boolean isKeepChunksLoaded() {
        return keepChunksLoaded;
    }

    public static boolean isTracked(String key) {
        return key != null && ENTRIES.containsKey(key);
    }

    /** Registers a linked location. Balanced by {@link #untrack(String)}. */
    public static void track(String key) {
        Location location = LinkedChestStorage.parseLocationKey(key);
        String world = worldOf(key);
        if (world == null) {
            return;
        }
        int x = location != null ? location.getBlockX() : coord(key, 3);
        int z = location != null ? location.getBlockZ() : coord(key, 1);
        boolean pin;
        Entry entry;
        synchronized (ENTRIES) {
            entry = ENTRIES.computeIfAbsent(key, k -> new Entry(k, world, x, z));
            entry.refs++;
            BY_CHUNK.computeIfAbsent(entry.chunkId(), c -> ConcurrentHashMap.newKeySet()).add(key);
            pin = entry.refs == 1 && keepChunksLoaded && location != null;
            if (pin) {
                entry.ticketHeld = true;
            }
        }
        if (pin) {
            ChunkTickets.acquire(location.getWorld(), entry.chunkX, entry.chunkZ);
        }
    }

    public static void untrack(String key) {
        Entry entry;
        synchronized (ENTRIES) {
            entry = ENTRIES.get(key);
            if (entry == null) {
                return;
            }
            entry.refs--;
            if (entry.refs > 0) {
                return;
            }
            ENTRIES.remove(key);
            Set<String> keys = BY_CHUNK.get(entry.chunkId());
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    BY_CHUNK.remove(entry.chunkId());
                }
            }
        }
        if (entry.ticketHeld) {
            ChunkTickets.release(entry.world, entry.chunkX, entry.chunkZ);
        }
        if (entry.partnerChunk != null) {
            String[] parts = entry.partnerChunk.split(":");
            ChunkTickets.release(entry.world,
                    Integer.parseInt(parts[parts.length - 2]), Integer.parseInt(parts[parts.length - 1]));
        }
    }

    /**
     * Pins the chunk holding the other half of a double chest when it differs from the linked
     * block's own chunk. The double inventory is only complete while both halves are loaded.
     */
    static void holdPartnerChunk(String key, World world, int chunkX, int chunkZ) {
        if (!keepChunksLoaded || world == null) {
            return;
        }
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            if (entry == null || entry.partnerChunk != null
                    || (entry.chunkX == chunkX && entry.chunkZ == chunkZ)) {
                return;
            }
            entry.partnerChunk = chunkId(world.getName(), chunkX, chunkZ);
        }
        ChunkTickets.acquire(world, chunkX, chunkZ);
    }

    /** Cached snapshot, or null when the chest has never been read. */
    static Snapshot snapshot(String key) {
        Entry entry = ENTRIES.get(key);
        return entry == null ? null : entry.snapshot;
    }

    /** True when the cached snapshot can be used without re-reading the live inventory. */
    static boolean isFresh(String key) {
        Entry entry = ENTRIES.get(key);
        if (entry == null || entry.dirty || entry.snapshot == null) {
            return false;
        }
        return System.currentTimeMillis() - entry.snapshot.takenAt() < MAX_AGE_MILLIS;
    }

    /**
     * Rebuilds {@code key}'s snapshot from its live inventory. Must run on the thread that owns
     * the chest. Returns null for untracked locations, which are never cached.
     */
    static Snapshot store(String key, Inventory inventory, int blockCount) {
        Entry entry = ENTRIES.get(key);
        if (entry == null || inventory == null) {
            return null;
        }
        // Cleared before reading, so a markDirty that lands mid-read is kept.
        entry.dirty = false;
        Map<String, StoredStack> totals = new LinkedHashMap<>();
        Map<String, Long> partialRoom = new HashMap<>();
        int empty = 0;
        long total = 0;
        int maxStack = inventory.getMaxStackSize();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot == null || slot.getType().isAir()) {
                empty++;
                continue;
            }
            String itemKey = entry.keyAt(i, slot);
            int amount = slot.getAmount();
            total += amount;
            StoredStack existing = totals.get(itemKey);
            if (existing == null) {
                StoredStack stack = new StoredStack(slot, amount);
                stack.setCachedItemKey(itemKey);
                totals.put(itemKey, stack);
            } else {
                existing.setAmount(existing.getAmount() + amount);
            }
            int room = Math.min(maxStack, slot.getMaxStackSize()) - amount;
            if (room > 0) {
                partialRoom.merge(itemKey, (long) room, Long::sum);
            }
        }
        Snapshot snapshot = new Snapshot(
                Collections.unmodifiableMap(totals),
                Collections.unmodifiableMap(partialRoom),
                empty,
                total,
                blockCount,
                System.currentTimeMillis());
        entry.snapshot = snapshot;
        return snapshot;
    }

    /** Item key for a live slot of a tracked chest, reusing that slot's cached hash. */
    static String keyAt(String key, int slot, ItemStack stack) {
        Entry entry = ENTRIES.get(key);
        return entry == null ? StoredStack.itemKey(stack) : entry.keyAt(slot, stack);
    }

    public static void markDirty(String key) {
        if (key == null) {
            return;
        }
        Entry entry = ENTRIES.get(key);
        if (entry != null) {
            entry.dirty = true;
        }
    }

    /**
     * Marks the linked chest at an inventory location dirty. Double chest inventories report
     * the midpoint between their halves, so both neighbouring block positions are checked.
     */
    public static void markDirtyAt(Location location) {
        if (location == null || ENTRIES.isEmpty()) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        String name = world.getName();
        int y = location.getBlockY();
        int x0 = (int) Math.floor(location.getX());
        int x1 = (int) Math.ceil(location.getX());
        int z0 = (int) Math.floor(location.getZ());
        int z1 = (int) Math.ceil(location.getZ());
        markDirty(NetworkManager.locationKey(name, x0, y, z0));
        if (x1 != x0 || z1 != z0) {
            markDirty(NetworkManager.locationKey(name, x1, y, z1));
        }
    }

    /** Keys of tracked locations inside the given chunk. */
    static List<String> keysInChunk(Chunk chunk) {
        Set<String> keys = BY_CHUNK.get(chunkId(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
        return keys == null ? List.of() : List.copyOf(keys);
    }

    public static void onChunkLoad(Chunk chunk) {
        for (String key : keysInChunk(chunk)) {
            markDirty(key);
        }
    }

    /**
     * Captures the final contents of every tracked chest in an unloading chunk. The chunk is
     * still accessible during the unload event, and the snapshot stays exact until it loads again.
     */
    public static void onChunkUnload(Chunk chunk) {
        for (String key : keysInChunk(chunk)) {
            LinkedChestStorage.snapshotUnloading(chunk, key);
        }
    }

    /** Pins linked chests in a world that was not loaded yet when they were tracked. */
    public static void onWorldLoad(World world) {
        if (!keepChunksLoaded || world == null) {
            return;
        }
        List<Entry> toPin = new java.util.ArrayList<>();
        synchronized (ENTRIES) {
            for (Entry entry : ENTRIES.values()) {
                if (!entry.ticketHeld && entry.refs > 0 && entry.world.equals(world.getName())) {
                    entry.ticketHeld = true;
                    toPin.add(entry);
                }
            }
        }
        for (Entry entry : toPin) {
            ChunkTickets.acquire(world, entry.chunkX, entry.chunkZ);
        }
    }

    /** Forgets all cached state. Tickets are released separately via {@link ChunkTickets}. */
    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
            BY_CHUNK.clear();
        }
    }

    private static String worldOf(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return null;
        }
        return String.join(":", java.util.Arrays.copyOf(parts, parts.length - 3));
    }

    /** Coordinate {@code fromEnd} places from the end of a {@code world:x:y:z} key (1 = z, 3 = x). */
    private static int coord(String key, int fromEnd) {
        String[] parts = key.split(":");
        try {
            return Integer.parseInt(parts[parts.length - fromEnd]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }
}
