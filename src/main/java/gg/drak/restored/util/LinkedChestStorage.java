package gg.drak.restored.util;

import gg.drak.restored.Restored;
import gg.drak.restored.config.MainConfig;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.serialization.PersistedItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to chests and barrels linked to a network. Contents are not DB-persisted — only link
 * locations are.
 *
 * <p>Reads (listings, amounts, free-space checks) are answered from {@link LinkedChestCache},
 * which also covers chests whose chunks are not loaded. Inserts and extractions always act on
 * the live inventory, and only on chests the current thread can touch, so a stale snapshot can
 * make an operation move less than hoped but can never duplicate or lose items.
 */
public final class LinkedChestStorage {

    private LinkedChestStorage() {
    }

    /**
     * True when the chunk containing this location is already loaded. Callers use this to
     * avoid {@link Location#getBlock()}, which force-loads the chunk synchronously.
     */
    public static boolean isChunkLoaded(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * True when the block at {@code location} is loaded and owned by the current thread — the
     * main thread on Paper, the owning region thread on Folia.
     */
    static boolean canTouch(Location location) {
        return isChunkLoaded(location) && Bukkit.isOwnedByCurrentRegion(location);
    }

    public static Location parseLocationKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return null;
        }
        try {
            // world names can contain ':' — take last 3 as coords
            int z = Integer.parseInt(parts[parts.length - 1]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int x = Integer.parseInt(parts[parts.length - 3]);
            StringBuilder worldBuilder = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length - 3; i++) {
                worldBuilder.append(':').append(parts[i]);
            }
            World world = Bukkit.getWorld(worldBuilder.toString());
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Canonical block for a chest inventory (LEFT half of a double chest, or the single block).
     */
    public static Block canonicalChestBlock(Block block) {
        if (block == null || block.getType() != Material.CHEST) {
            return null;
        }
        if (!(block.getBlockData() instanceof Chest chestData)) {
            return block;
        }
        if (chestData.getType() == Chest.Type.SINGLE || chestData.getType() == Chest.Type.LEFT) {
            return block;
        }
        Block other = otherHalf(block, chestData);
        return other != null ? other : block;
    }

    /** Canonical linked storage block: a chest half or a barrel. */
    public static Block canonicalStorageBlock(Block block) {
        if (block == null) {
            return null;
        }
        if (block.getType() == Material.CHEST) {
            return canonicalChestBlock(block);
        }
        return block.getType() == Material.BARREL ? block : null;
    }

    public static boolean isSupportedStorage(Block block) {
        return canonicalStorageBlock(block) != null;
    }

    public static Block otherHalf(Block block, Chest chestData) {
        BlockFace offset = otherHalfFace(chestData);
        if (offset == null) {
            return null;
        }
        Block other = block.getRelative(offset);
        return other.getType() == Material.CHEST ? other : null;
    }

    /** Direction from one half of a double chest to the other, or null for a single chest. */
    private static BlockFace otherHalfFace(Chest chestData) {
        if (chestData.getType() == Chest.Type.SINGLE) {
            return null;
        }
        return switch (chestData.getFacing()) {
            case NORTH -> chestData.getType() == Chest.Type.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case SOUTH -> chestData.getType() == Chest.Type.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case EAST -> chestData.getType() == Chest.Type.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            case WEST -> chestData.getType() == Chest.Type.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> null;
        };
    }

    public static boolean isWithinLinkRange(Network network, Location chestLocation) {
        if (network == null || !network.isPlaced() || chestLocation == null || chestLocation.getWorld() == null) {
            return false;
        }
        if (!network.getWorld().equals(chestLocation.getWorld().getName())) {
            return false;
        }
        int maxDistance = getMaxLinkDistance();
        if (maxDistance == -1) {
            return true;
        }
        double dx = (network.getX() + 0.5) - (chestLocation.getBlockX() + 0.5);
        double dy = (network.getY() + 0.5) - (chestLocation.getBlockY() + 0.5);
        double dz = (network.getZ() + 0.5) - (chestLocation.getBlockZ() + 0.5);
        double maxDistanceSquared = (double) maxDistance * maxDistance;
        return (dx * dx + dy * dy + dz * dz) <= maxDistanceSquared;
    }

    public static boolean allLinksWithinRange(Network network, Location networkLocation) {
        if (network == null || networkLocation == null || networkLocation.getWorld() == null) {
            return network == null || network.getLinkedChestCount() == 0;
        }
        String worldName = networkLocation.getWorld().getName();
        int maxDistance = getMaxLinkDistance();
        for (String key : network.getLinkedChestKeys()) {
            Location linked = parseLocationKey(key);
            if (linked == null || linked.getWorld() == null) {
                continue;
            }
            if (!worldName.equals(linked.getWorld().getName())) {
                return false;
            }
            if (maxDistance == -1) {
                continue;
            }
            double dx = (networkLocation.getBlockX() + 0.5) - (linked.getBlockX() + 0.5);
            double dy = (networkLocation.getBlockY() + 0.5) - (linked.getBlockY() + 0.5);
            double dz = (networkLocation.getBlockZ() + 0.5) - (linked.getBlockZ() + 0.5);
            double maxDistanceSquared = (double) maxDistance * maxDistance;
            if ((dx * dx + dy * dy + dz * dz) > maxDistanceSquared) {
                return false;
            }
        }
        return true;
    }

    public static int getMaxLinkDistance() {
        if (Restored.getMainConfig() == null) {
            return MainConfig.DEFAULT_LINKED_CHEST_MAX_DISTANCE;
        }
        return Restored.getMainConfig().getLinkedChestMaxDistance();
    }

    public static String getLinkDistanceDescription() {
        int maxDistance = getMaxLinkDistance();
        return maxDistance == -1 ? "any distance" : maxDistance + " blocks";
    }

    // ------------------------------------------------------------------------------------------
    // Live resolution
    // ------------------------------------------------------------------------------------------

    /** A linked chest this thread may modify right now, under its canonical key. */
    private record LiveChest(String key, Inventory inventory, int blockCount) {
    }

    /**
     * Resolves a linked key to its live inventory and repairs the link on the way: a location
     * that no longer holds supported storage is added to {@code dead}, and a link recorded on
     * the right half of a double chest moves to the canonical left half.
     *
     * @return null when the chest is gone, or is not currently loaded and owned by this thread.
     */
    private static LiveChest resolveLive(Network network, String key, List<String> dead) {
        Location location = parseLocationKey(key);
        // Never touch a block in an unloaded chunk: getBlock() would force a synchronous chunk
        // load, and on Folia a chunk owned by another region cannot be read at all.
        if (location == null || !canTouch(location)) {
            return null;
        }
        Block block = location.getBlock();
        // Resolving the other half of a double chest reads the neighbouring block, and the
        // double inventory reads it too; both would load that chunk synchronously.
        if (!partnerReachable(key, block)) {
            return null;
        }
        Block canonical = canonicalStorageBlock(block);
        if (canonical == null) {
            dead.add(key);
            return null;
        }
        String canonicalKey = key;
        if (canonical != block) {
            if (NetworkHopperStorage.isHopper(block)) {
                dead.add(key);
                return null;
            }
            canonicalKey = NetworkManager.locationKey(canonical.getLocation());
            dead.add(key);
            if (!network.hasLinkedChestKey(canonicalKey)) {
                network.addLinkedChest(
                        canonical.getWorld().getName(),
                        canonical.getX(),
                        canonical.getY(),
                        canonical.getZ()
                );
                NetworkBlockTags.setLinkedNetworkId(canonical, network.getIdentifier());
            }
            NetworkBlockTags.clearLinkedNetworkId(block);
            if (!canTouch(canonical.getLocation())) {
                return null;
            }
        }
        // Non-snapshot state: reads the live tile entity instead of copying the whole inventory.
        BlockState state = canonical.getState(false);
        if (state instanceof TileState tile && NetworkBlockTags.getHopperRole(tile).isPresent()) {
            dead.add(canonicalKey);
            return null;
        }
        if (!(state instanceof Container container)) {
            return null;
        }
        int blockCount = 1;
        if (canonical.getBlockData() instanceof Chest chestData) {
            BlockFace face = otherHalfFace(chestData);
            if (face != null) {
                blockCount = 2;
                int partnerX = canonical.getX() + face.getModX();
                int partnerZ = canonical.getZ() + face.getModZ();
                LinkedChestCache.holdPartnerChunk(
                        canonicalKey, canonical.getWorld(), partnerX >> 4, partnerZ >> 4);
            }
        }
        return new LiveChest(canonicalKey, container.getInventory(), blockCount);
    }

    /**
     * False when {@code block} is half of a double chest whose other half sits in a chunk this
     * thread cannot touch. Single chests, barrels and other blocks are always reachable.
     *
     * <p>The partner's chunk is pinned for {@code key} before the check, so with keep-loaded on
     * a double chest straddling a chunk border becomes reachable once that chunk has loaded.
     */
    private static boolean partnerReachable(String key, Block block) {
        if (block.getType() != Material.CHEST || !(block.getBlockData() instanceof Chest chestData)) {
            return true;
        }
        BlockFace face = otherHalfFace(chestData);
        if (face == null) {
            return true;
        }
        int partnerX = block.getX() + face.getModX();
        int partnerZ = block.getZ() + face.getModZ();
        LinkedChestCache.holdPartnerChunk(key, block.getWorld(), partnerX >> 4, partnerZ >> 4);
        return canTouch(new Location(block.getWorld(), partnerX, block.getY(), partnerZ));
    }

    private static void removeDead(Network network, List<String> dead) {
        for (String key : dead) {
            network.removeLinkedChestKey(key);
        }
    }

    /**
     * Snapshot of a linked chest as its chunk unloads. Read-only: links are never repaired from
     * here, because the owning network is not known and the block is about to disappear.
     */
    static void snapshotUnloading(org.bukkit.Chunk chunk, String key) {
        Location location = parseLocationKey(key);
        if (location == null) {
            return;
        }
        // Read through the event's chunk: it is still accessible while unloading, even where
        // World#isChunkLoaded may already report it as gone.
        Block block = chunk.getBlock(location.getBlockX() & 15, location.getBlockY(), location.getBlockZ() & 15);
        Material type = block.getType();
        int blockCount = 1;
        if (type == Material.CHEST && block.getBlockData() instanceof Chest chestData) {
            if (chestData.getType() == Chest.Type.RIGHT) {
                return;
            }
            BlockFace face = otherHalfFace(chestData);
            if (face != null) {
                int partnerX = block.getX() + face.getModX();
                int partnerZ = block.getZ() + face.getModZ();
                boolean sameChunk = partnerX >> 4 == chunk.getX() && partnerZ >> 4 == chunk.getZ();
                // A partner half in a neighbouring chunk that is not loaded would be loaded again
                // by reading the double inventory.
                if (!sameChunk && !block.getWorld().isChunkLoaded(partnerX >> 4, partnerZ >> 4)) {
                    return;
                }
                blockCount = 2;
            }
        } else if (type != Material.BARREL) {
            return;
        }
        if (!(block.getState(false) instanceof Container container)) {
            return;
        }
        LinkedChestCache.store(key, container.getInventory(), blockCount);
    }

    /** One linked chest's contents as seen by readers. */
    private record LinkedView(String key, LinkedChestCache.Snapshot snapshot, boolean live) {
    }

    /**
     * Every linked chest of {@code network} with its best-known contents. Fresh cached snapshots
     * are used as-is; stale ones are rebuilt when the chest can be touched, and otherwise the last
     * snapshot stands in (exact for unloaded chunks, since nothing changes there). A chest with no
     * snapshot yet appears with a null snapshot.
     */
    private static List<LinkedView> views(Network network) {
        List<LinkedView> views = new ArrayList<>();
        if (network == null) {
            return views;
        }
        Map<String, LinkedView> byKey = new LinkedHashMap<>();
        List<String> dead = new ArrayList<>();
        for (String key : new ArrayList<>(network.getLinkedChestKeys())) {
            if (byKey.containsKey(key)) {
                continue;
            }
            // False for malformed (untracked) keys and for worlds that are not loaded.
            boolean touchable = LinkedChestCache.isTouchable(key);
            if (LinkedChestCache.isFresh(key)) {
                byKey.put(key, new LinkedView(key, LinkedChestCache.snapshot(key), touchable));
                continue;
            }
            if (!touchable) {
                byKey.put(key, new LinkedView(key, LinkedChestCache.snapshot(key), false));
                continue;
            }
            LiveChest live = resolveLive(network, key, dead);
            if (live == null) {
                if (!dead.contains(key)) {
                    byKey.put(key, new LinkedView(key, LinkedChestCache.snapshot(key), false));
                }
                continue;
            }
            // A migrated link resolves under its canonical key, which the loop may also visit.
            if (byKey.containsKey(live.key())) {
                continue;
            }
            LinkedChestCache.Snapshot snapshot =
                    LinkedChestCache.store(live.key(), live.inventory(), live.blockCount());
            byKey.put(live.key(), new LinkedView(live.key(), snapshot, true));
        }
        removeDead(network, dead);
        for (LinkedView view : byKey.values()) {
            if (!dead.contains(view.key())) {
                views.add(view);
            }
        }
        return views;
    }

    /**
     * Number of linked locations whose contents are unknown: not loaded and never seen since the
     * server started. Their items are missing from every listing until the chunk loads once.
     */
    public static int countUnavailableLinks(Network network) {
        if (network == null) {
            return 0;
        }
        int unavailable = 0;
        for (String key : network.getLinkedChestKeys()) {
            Location location = parseLocationKey(key);
            if (location == null) {
                unavailable++;
            } else if (LinkedChestCache.snapshot(key) == null && !canTouch(location)) {
                unavailable++;
            }
        }
        return unavailable;
    }

    // ------------------------------------------------------------------------------------------
    // Chunk leases for remote browsing
    // ------------------------------------------------------------------------------------------

    /**
     * Chunks held loaded so a network's linked chests stay writable while a player browses the
     * network from afar. Leases are keyed by player rather than by GUI instance: navigating to a
     * sub-menu or opening the chat search prompt closes the current inventory, and a GUI-scoped
     * lease would be dropped there — letting the chunks unload partway through the session.
     */
    public static final class LinkedChunkLease {
        private final List<Object[]> held = new ArrayList<>();
        private boolean released;

        private synchronized boolean hold(World world, int chunkX, int chunkZ) {
            if (released || world == null) {
                return false;
            }
            held.add(new Object[]{world, chunkX, chunkZ});
            return true;
        }

        public void release() {
            List<Object[]> toRelease;
            synchronized (this) {
                if (released) {
                    return;
                }
                released = true;
                toRelease = new ArrayList<>(held);
                held.clear();
            }
            for (Object[] chunk : toRelease) {
                ChunkTickets.release((World) chunk[0], (int) chunk[1], (int) chunk[2]);
            }
        }
    }

    private static final Map<java.util.UUID, LinkedChunkLease> PLAYER_LEASES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Releases any linked-chunk tickets held for this player. Called when they close out of the
     * network GUI family or log off; safe to call when no lease is held.
     */
    public static void releaseLease(java.util.UUID playerId) {
        if (playerId == null) {
            return;
        }
        LinkedChunkLease lease = PLAYER_LEASES.remove(playerId);
        if (lease != null) {
            lease.release();
        }
    }

    public static boolean hasLease(java.util.UUID playerId) {
        return playerId != null && PLAYER_LEASES.containsKey(playerId);
    }

    /**
     * Loads every linked chest's chunk and keeps it loaded for {@code playerId}, then runs
     * {@code afterLoad} on a thread that may touch them. Views opened away from the network (the
     * Pocket Link) use this so withdrawals and deposits can reach every linked chest; the cache
     * alone can list their contents but never modifies an unloaded chest.
     *
     * <p>Chunk loading is asynchronous, so the main thread is never stalled. {@code afterLoad}
     * always runs exactly once, even when some chunks fail to load. Any lease the player already
     * held is released first, and the new one lives until {@link #releaseLease(java.util.UUID)}.
     *
     * @param playerId player the tickets are held for, or null for a one-shot operation whose
     *                 lease is released as soon as {@code afterLoad} returns.
     */
    public static void prepareLinkedChunks(Network network, java.util.UUID playerId, Runnable afterLoad) {
        if (network == null) {
            afterLoad.run();
            return;
        }
        List<Location> linked = new ArrayList<>();
        for (String key : new ArrayList<>(network.getLinkedChestKeys())) {
            Location location = parseLocationKey(key);
            if (location != null && location.getWorld() != null) {
                linked.add(location);
            }
        }
        if (linked.isEmpty()) {
            afterLoad.run();
            return;
        }
        LinkedChunkLease lease = new LinkedChunkLease();
        // Links may legitimately span worlds, so chunks are identified per world rather than
        // assuming every chunk coordinate belongs to the first link's world.
        java.util.Set<String> seenChunks = new java.util.HashSet<>();
        List<java.util.concurrent.CompletableFuture<?>> futures = new ArrayList<>();
        for (Location location : linked) {
            World world = location.getWorld();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!seenChunks.add(world.getName() + ':' + chunkX + ':' + chunkZ)) {
                continue;
            }
            // The reference is recorded before the async load finishes, so releasing the lease
            // early still balances every acquire.
            if (lease.hold(world, chunkX, chunkZ)) {
                futures.add(ChunkTickets.acquire(world, chunkX, chunkZ));
            }
        }
        Location anchor = linked.get(0);
        java.util.concurrent.CompletableFuture
                .allOf(futures.toArray(new java.util.concurrent.CompletableFuture<?>[0]))
                .whenComplete((ignored, error) -> PlatformScheduler.runAtLocation(anchor, () -> {
                    if (playerId == null) {
                        try {
                            afterLoad.run();
                        } finally {
                            lease.release();
                        }
                        return;
                    }
                    LinkedChunkLease previous = PLAYER_LEASES.put(playerId, lease);
                    if (previous != null) {
                        previous.release();
                    }
                    afterLoad.run();
                }));
    }

    // ------------------------------------------------------------------------------------------
    // Writes (live inventories only)
    // ------------------------------------------------------------------------------------------

    public static long insertIntoLinked(Network network, ItemStack stack, long amount) {
        if (network == null || stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        String itemKey = StoredStack.itemKey(stack);
        long remaining = amount;
        long inserted = 0;
        List<String> dead = new ArrayList<>();
        for (LinkedView view : views(network)) {
            if (remaining <= 0) {
                break;
            }
            if (!view.live() || view.snapshot() == null || !view.snapshot().hasRoomFor(itemKey)) {
                continue;
            }
            LiveChest live = resolveLive(network, view.key(), dead);
            if (live == null) {
                continue;
            }
            long placed = addToInventory(live.inventory(), stack, remaining);
            LinkedChestCache.store(live.key(), live.inventory(), live.blockCount());
            inserted += placed;
            remaining -= placed;
        }
        removeDead(network, dead);
        return inserted;
    }

    private static long addToInventory(Inventory inventory, ItemStack stack, long amount) {
        long remaining = amount;
        long inserted = 0;
        while (remaining > 0) {
            int batch = (int) Math.min(remaining, stack.getMaxStackSize());
            ItemStack toAdd = stack.clone();
            toAdd.setAmount(batch);
            Map<Integer, ItemStack> leftover = inventory.addItem(toAdd);
            int placed = batch;
            if (!leftover.isEmpty()) {
                ItemStack left = leftover.values().iterator().next();
                placed = batch - left.getAmount();
            }
            if (placed <= 0) {
                break;
            }
            inserted += placed;
            remaining -= placed;
            if (!leftover.isEmpty()) {
                break;
            }
        }
        return inserted;
    }

    public static long extractFromLinked(Network network, String itemKey, long amount) {
        if (network == null || itemKey == null || amount <= 0) {
            return 0;
        }
        long remaining = amount;
        long taken = 0;
        List<String> dead = new ArrayList<>();
        for (LinkedView view : views(network)) {
            if (remaining <= 0) {
                break;
            }
            if (!view.live() || view.snapshot() == null || view.snapshot().amount(itemKey) <= 0) {
                continue;
            }
            LiveChest live = resolveLive(network, view.key(), dead);
            if (live == null) {
                continue;
            }
            StoredStack cached = view.snapshot().totals().get(itemKey);
            Material keyMaterial = cached == null || cached.getTemplate() == null
                    ? null : cached.getTemplate().getType();
            long removed = removeFromInventory(live, itemKey, remaining, keyMaterial);
            LinkedChestCache.store(live.key(), live.inventory(), live.blockCount());
            taken += removed;
            remaining -= removed;
        }
        removeDead(network, dead);
        return taken;
    }

    private static long removeFromInventory(LiveChest live, String itemKey, long amount, Material keyMaterial) {
        Inventory inventory = live.inventory();
        long remaining = amount;
        long taken = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (slot == null || slot.getType().isAir()
                    || PersistedItemCodec.cannotMatch(slot, keyMaterial)) {
                continue;
            }
            if (!itemKey.equals(LinkedChestCache.keyAt(live.key(), i, slot))) {
                continue;
            }
            int remove = (int) Math.min(remaining, slot.getAmount());
            int left = slot.getAmount() - remove;
            if (left <= 0) {
                inventory.setItem(i, null);
            } else {
                ItemStack copy = slot.clone();
                copy.setAmount(left);
                inventory.setItem(i, copy);
            }
            taken += remove;
            remaining -= remove;
        }
        return taken;
    }

    // ------------------------------------------------------------------------------------------
    // Reads (served from the cache)
    // ------------------------------------------------------------------------------------------

    public static Map<String, StoredStack> aggregateLinkedByKey(Network network) {
        return aggregateLinkedByKey(network, null);
    }

    /**
     * Linked contents merged by item key. Every returned stack is a fresh object the caller may
     * modify.
     *
     * @param accept optional Material test; callers that only care about one family of items
     *               (arrows, food, rockets) pass it to skip everything else.
     */
    public static Map<String, StoredStack> aggregateLinkedByKey(
            Network network, java.util.function.Predicate<Material> accept) {
        Map<String, StoredStack> aggregated = new LinkedHashMap<>();
        for (LinkedView view : views(network)) {
            if (view.snapshot() == null) {
                continue;
            }
            for (Map.Entry<String, StoredStack> entry : view.snapshot().totals().entrySet()) {
                StoredStack cached = entry.getValue();
                if (accept != null && (cached.getTemplate() == null || !accept.test(cached.getTemplate().getType()))) {
                    continue;
                }
                StoredStack existing = aggregated.get(entry.getKey());
                if (existing == null) {
                    StoredStack copy = new StoredStack(cached.getTemplate(), cached.getAmount());
                    copy.setCachedItemKey(entry.getKey());
                    aggregated.put(entry.getKey(), copy);
                } else {
                    existing.setAmount(existing.getAmount() + cached.getAmount());
                }
            }
        }
        return aggregated;
    }

    /**
     * Item key → amount held in linked chests that can be extracted from right now, for callers
     * checking many keys at once.
     */
    public static Map<String, Long> linkedAmounts(Network network) {
        Map<String, Long> amounts = new java.util.HashMap<>();
        for (LinkedView view : views(network)) {
            if (!view.live() || view.snapshot() == null) {
                continue;
            }
            for (Map.Entry<String, StoredStack> entry : view.snapshot().totals().entrySet()) {
                amounts.merge(entry.getKey(), entry.getValue().getAmount(), Long::sum);
            }
        }
        return amounts;
    }

    public static long countLinkedItems(Network network) {
        long total = 0;
        for (LinkedView view : views(network)) {
            if (view.snapshot() != null) {
                total += view.snapshot().totalItems();
            }
        }
        return total;
    }

    /**
     * Returns true when a linked location cannot currently be inspected, usually because its
     * world is not loaded. Callers that are deciding whether it is safe to delete a network must
     * treat such links as non-empty because their physical contents are unknown.
     */
    public static boolean hasUnresolvedLinks(Network network) {
        if (network == null) {
            return false;
        }
        for (String key : network.getLinkedChestKeys()) {
            if (parseLocationKey(key) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts physical chest blocks for display. A double chest counts as 2. Links whose block
     * has never been seen count as 1 for the stored location.
     */
    public static int countLinkedChestBlocks(Network network) {
        if (network == null) {
            return 0;
        }
        int total = 0;
        for (LinkedView view : views(network)) {
            total += view.snapshot() == null ? 1 : view.snapshot().blockCount();
        }
        return total;
    }

    public static long extractableAmount(Network network, String itemKey) {
        if (network == null || itemKey == null) {
            return 0;
        }
        long total = 0;
        for (LinkedView view : views(network)) {
            if (view.live() && view.snapshot() != null) {
                total += view.snapshot().amount(itemKey);
            }
        }
        return total;
    }

    /** True when some of {@code itemKey} is known to sit in linked chests that cannot be reached now. */
    public static boolean hasUnreachableAmount(Network network, String itemKey) {
        if (network == null || itemKey == null) {
            return false;
        }
        for (LinkedView view : views(network)) {
            if (!view.live() && view.snapshot() != null && view.snapshot().amount(itemKey) > 0) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack findTemplate(Network network, String itemKey) {
        if (network == null || itemKey == null) {
            return null;
        }
        for (LinkedView view : views(network)) {
            if (view.snapshot() == null) {
                continue;
            }
            StoredStack cached = view.snapshot().totals().get(itemKey);
            if (cached != null && cached.getTemplate() != null) {
                ItemStack template = cached.getTemplate().clone();
                template.setAmount(1);
                return template;
            }
        }
        return null;
    }

    /** True if any reachable linked chest has at least one empty slot. */
    public static boolean hasAnyFreeSlot(Network network) {
        for (LinkedView view : views(network)) {
            if (view.live() && view.snapshot() != null && view.snapshot().emptySlots() > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLinkedSpace(Network network, ItemStack stack) {
        if (network == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        String key = StoredStack.itemKey(stack);
        for (LinkedView view : views(network)) {
            if (view.live() && view.snapshot() != null && view.snapshot().hasRoomFor(key)) {
                return true;
            }
        }
        return false;
    }
}
