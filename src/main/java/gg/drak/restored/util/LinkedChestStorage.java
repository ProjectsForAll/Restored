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
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live inventory access for chests linked to a network.
 * Contents are not DB-persisted — only link locations are.
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
        BlockFace facing = chestData.getFacing();
        BlockFace offset = switch (facing) {
            case NORTH -> chestData.getType() == Chest.Type.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case SOUTH -> chestData.getType() == Chest.Type.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case EAST -> chestData.getType() == Chest.Type.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            case WEST -> chestData.getType() == Chest.Type.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> null;
        };
        if (offset == null) {
            return null;
        }
        Block other = block.getRelative(offset);
        return other.getType() == Material.CHEST ? other : null;
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

    public static List<Inventory> resolveInventories(Network network) {
        List<Inventory> inventories = new ArrayList<>();
        // Double chests resolve to the same Inventory from either half; dedupe by identity
        // instead of List.contains, which is O(n^2) and calls Inventory.equals per link.
        java.util.Set<Inventory> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (network == null) {
            return inventories;
        }
        List<String> keys = new ArrayList<>(network.getLinkedChestKeys());
        List<String> dead = new ArrayList<>();
        for (String key : keys) {
            Location location = parseLocationKey(key);
            if (location == null || location.getWorld() == null) {
                continue;
            }
            // Never touch a block in an unloaded chunk. location.getBlock() would force a
            // synchronous chunk load on the main thread, and the resulting AIR read would
            // fall through to the dead-link path below and silently unlink a real chest.
            if (!isChunkLoaded(location)) {
                continue;
            }
            Block block = location.getBlock();
            if (!isSupportedStorage(block) || NetworkHopperStorage.isHopper(block)) {
                dead.add(key);
                continue;
            }
            Block canonical = canonicalStorageBlock(block);
            if (canonical == null) {
                dead.add(key);
                continue;
            }
            String canonicalKey = NetworkManager.locationKey(canonical.getLocation());
            if (!canonicalKey.equals(key)) {
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
            }
            if (!(canonical.getState() instanceof Container container)) {
                continue;
            }
            Inventory inventory = container.getInventory();
            if (seen.add(inventory)) {
                inventories.add(inventory);
            }
        }
        for (String key : dead) {
            network.removeLinkedChestKey(key);
        }
        return inventories;
    }

    /**
     * Number of linked locations whose chunk is not currently loaded, and whose contents are
     * therefore invisible to {@link #resolveInventories(Network)}.
     */
    public static int countUnavailableLinks(Network network) {
        if (network == null) {
            return 0;
        }
        int unavailable = 0;
        for (String key : network.getLinkedChestKeys()) {
            Location location = parseLocationKey(key);
            if (location == null || location.getWorld() == null || !isChunkLoaded(location)) {
                unavailable++;
            }
        }
        return unavailable;
    }

    /**
     * Plugin chunk tickets held so a network's linked chests stay loaded while a player browses
     * the network from afar. Leases are keyed by player rather than by GUI instance: navigating
     * to a sub-menu or opening the chat search prompt closes the current inventory, and a
     * GUI-scoped lease would be dropped there — letting the chunks unload and making the items
     * disappear again partway through the session.
     */
    public static final class LinkedChunkLease {
        private final Map<World, List<long[]>> ticketed = new LinkedHashMap<>();
        private boolean released;

        private void hold(World world, int chunkX, int chunkZ) {
            if (world == null) {
                return;
            }
            world.addPluginChunkTicket(chunkX, chunkZ, Restored.getInstance());
            ticketed.computeIfAbsent(world, w -> new ArrayList<>()).add(new long[]{chunkX, chunkZ});
        }

        public void release() {
            if (released) {
                return;
            }
            released = true;
            for (Map.Entry<World, List<long[]>> entry : ticketed.entrySet()) {
                for (long[] chunk : entry.getValue()) {
                    entry.getKey().removePluginChunkTicket(
                            (int) chunk[0], (int) chunk[1], Restored.getInstance());
                }
            }
            ticketed.clear();
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
     * Pocket Link) use this so linked-chest contents are actually visible; without it
     * {@link #resolveInventories(Network)} silently skips unloaded chunks and the network appears
     * to be missing most of its items.
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
        // Links may legitimately span worlds, so tickets are grouped per world rather than
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
            // Load first, ticket in the callback: addPluginChunkTicket can load synchronously,
            // and ticketing every chunk up front would stall the main thread on a single click.
            futures.add(PlatformScheduler.loadChunk(location)
                    .thenRun(() -> lease.hold(world, chunkX, chunkZ)));
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

    public static long insertIntoLinked(Network network, ItemStack stack, long amount) {
        if (network == null || stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        return insertIntoLinked(resolveInventories(network), stack, amount);
    }

    public static long insertIntoLinked(List<Inventory> inventories, ItemStack stack, long amount) {
        if (stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        long remaining = amount;
        long inserted = 0;
        for (Inventory inventory : inventories) {
            if (remaining <= 0) {
                break;
            }
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
        }
        return inserted;
    }

    public static long extractFromLinked(Network network, String itemKey, long amount) {
        if (network == null || itemKey == null || amount <= 0) {
            return 0;
        }
        return extractFromLinked(resolveInventories(network), itemKey, amount);
    }

    public static long extractFromLinked(List<Inventory> inventories, String itemKey, long amount) {
        return extractFromLinked(inventories, itemKey, amount, null);
    }

    /**
     * @param keyMaterial Material {@code itemKey} refers to, when the caller knows it. Used only
     *                    to reject slots without hashing; null simply disables that shortcut.
     */
    public static long extractFromLinked(
            List<Inventory> inventories, String itemKey, long amount, Material keyMaterial) {
        if (itemKey == null || amount <= 0) {
            return 0;
        }
        long remaining = amount;
        long taken = 0;
        for (Inventory inventory : inventories) {
            if (remaining <= 0) {
                break;
            }
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot == null || slot.getType().isAir()
                        || PersistedItemCodec.cannotMatch(slot, keyMaterial)) {
                    continue;
                }
                if (!itemKey.equals(StoredStack.itemKey(slot))) {
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
        }
        return taken;
    }

    public static Map<String, StoredStack> aggregateLinkedByKey(Network network) {
        Map<String, StoredStack> aggregated = new LinkedHashMap<>();
        if (network == null) {
            return aggregated;
        }
        return aggregateLinkedByKey(resolveInventories(network));
    }

    public static Map<String, StoredStack> aggregateLinkedByKey(List<Inventory> inventories) {
        return aggregateLinkedByKey(inventories, null);
    }

    /**
     * @param accept optional Material test applied before hashing. Callers that only care about
     *               one family of items (arrows, food, rockets) pass it so the SHA-256 in
     *               {@link StoredStack#itemKey} is never computed for slots they would discard.
     */
    public static Map<String, StoredStack> aggregateLinkedByKey(
            List<Inventory> inventories, java.util.function.Predicate<Material> accept) {
        Map<String, StoredStack> aggregated = new LinkedHashMap<>();
        for (Inventory inventory : inventories) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()
                        || (accept != null && !accept.test(slot.getType()))) {
                    continue;
                }
                String key = StoredStack.itemKey(slot);
                StoredStack existing = aggregated.get(key);
                if (existing == null) {
                    aggregated.put(key, new StoredStack(slot, slot.getAmount()));
                } else {
                    existing.setAmount(existing.getAmount() + slot.getAmount());
                }
            }
        }
        return aggregated;
    }

    public static long countLinkedItems(Network network) {
        long total = 0;
        for (StoredStack stack : aggregateLinkedByKey(network).values()) {
            total += stack.getAmount();
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
     * Counts physical chest blocks for display. A double chest counts as 2.
     * Missing/unloaded links still count as 1 for the stored location.
     */
    public static int countLinkedChestBlocks(Network network) {
        if (network == null) {
            return 0;
        }
        int total = 0;
        for (String key : network.getLinkedChestKeys()) {
            Location location = parseLocationKey(key);
            if (location == null || location.getWorld() == null) {
                total += 1;
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() == Material.BARREL) {
                total += 1;
                continue;
            }
            if (block.getType() != Material.CHEST || !(block.getBlockData() instanceof Chest chestData)) {
                total += 1;
                continue;
            }
            if (chestData.getType() == Chest.Type.SINGLE) {
                total += 1;
            } else {
                total += 2;
            }
        }
        return total;
    }

    public static long extractableAmount(Network network, String itemKey) {
        if (network == null || itemKey == null) {
            return 0;
        }
        return extractableAmount(resolveInventories(network), itemKey);
    }

    public static long extractableAmount(List<Inventory> inventories, String itemKey) {
        return extractableAmount(inventories, itemKey, null);
    }

    /** @param keyMaterial see {@link #extractFromLinked(List, String, long, Material)}. */
    public static long extractableAmount(List<Inventory> inventories, String itemKey, Material keyMaterial) {
        if (itemKey == null) {
            return 0;
        }
        long total = 0;
        for (Inventory inventory : inventories) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()
                        || PersistedItemCodec.cannotMatch(slot, keyMaterial)) {
                    continue;
                }
                if (itemKey.equals(StoredStack.itemKey(slot))) {
                    total += slot.getAmount();
                }
            }
        }
        return total;
    }

    public static ItemStack findTemplate(Network network, String itemKey) {
        if (network == null || itemKey == null) {
            return null;
        }
        return findTemplate(resolveInventories(network), itemKey);
    }

    public static ItemStack findTemplate(List<Inventory> inventories, String itemKey) {
        if (itemKey == null) {
            return null;
        }
        for (Inventory inventory : inventories) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()) {
                    continue;
                }
                if (itemKey.equals(StoredStack.itemKey(slot))) {
                    ItemStack template = slot.clone();
                    template.setAmount(1);
                    return template;
                }
            }
        }
        return null;
    }

    /** True if any linked chest has at least one empty slot. */
    public static boolean hasAnyFreeSlot(Network network) {
        if (network == null) {
            return false;
        }
        for (Inventory inventory : resolveInventories(network)) {
            if (inventory.firstEmpty() >= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLinkedSpace(Network network, ItemStack stack) {
        if (network == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        return hasLinkedSpace(resolveInventories(network), stack);
    }

    public static boolean hasLinkedSpace(List<Inventory> inventories, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String key = StoredStack.itemKey(stack);
        Material keyMaterial = stack.getType();
        for (Inventory inventory : inventories) {
            if (inventory.firstEmpty() >= 0) {
                return true;
            }
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()
                        || PersistedItemCodec.cannotMatch(slot, keyMaterial)) {
                    continue;
                }
                if (key.equals(StoredStack.itemKey(slot)) && slot.getAmount() < slot.getMaxStackSize()) {
                    return true;
                }
            }
        }
        return false;
    }
}
