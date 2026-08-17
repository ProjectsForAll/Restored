package gg.drak.restored.util;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.StoredStack;
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

    public static final int MAX_LINK_DISTANCE = 64;
    private static final int MAX_LINK_DISTANCE_SQ = MAX_LINK_DISTANCE * MAX_LINK_DISTANCE;

    private LinkedChestStorage() {
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
        double dx = (network.getX() + 0.5) - (chestLocation.getBlockX() + 0.5);
        double dy = (network.getY() + 0.5) - (chestLocation.getBlockY() + 0.5);
        double dz = (network.getZ() + 0.5) - (chestLocation.getBlockZ() + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= MAX_LINK_DISTANCE_SQ;
    }

    public static boolean allLinksWithinRange(Network network, Location networkLocation) {
        if (network == null || networkLocation == null || networkLocation.getWorld() == null) {
            return network == null || network.getLinkedChestCount() == 0;
        }
        String worldName = networkLocation.getWorld().getName();
        for (String key : network.getLinkedChestKeys()) {
            Location linked = parseLocationKey(key);
            if (linked == null || linked.getWorld() == null) {
                continue;
            }
            if (!worldName.equals(linked.getWorld().getName())) {
                return false;
            }
            double dx = (networkLocation.getBlockX() + 0.5) - (linked.getBlockX() + 0.5);
            double dy = (networkLocation.getBlockY() + 0.5) - (linked.getBlockY() + 0.5);
            double dz = (networkLocation.getBlockZ() + 0.5) - (linked.getBlockZ() + 0.5);
            if ((dx * dx + dy * dy + dz * dz) > MAX_LINK_DISTANCE_SQ) {
                return false;
            }
        }
        return true;
    }

    public static List<Inventory> resolveInventories(Network network) {
        List<Inventory> inventories = new ArrayList<>();
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
            Block block = location.getBlock();
            if (block.getType() != Material.CHEST) {
                dead.add(key);
                continue;
            }
            Block canonical = canonicalChestBlock(block);
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
            if (!inventories.contains(inventory)) {
                inventories.add(inventory);
            }
        }
        for (String key : dead) {
            network.removeLinkedChestKey(key);
        }
        return inventories;
    }

    public static long insertIntoLinked(Network network, ItemStack stack, long amount) {
        if (network == null || stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        long remaining = amount;
        long inserted = 0;
        for (Inventory inventory : resolveInventories(network)) {
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
        long remaining = amount;
        long taken = 0;
        for (Inventory inventory : resolveInventories(network)) {
            if (remaining <= 0) {
                break;
            }
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (slot == null || slot.getType().isAir()) {
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
        for (Inventory inventory : resolveInventories(network)) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()) {
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
        long total = 0;
        for (Inventory inventory : resolveInventories(network)) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()) {
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
        for (Inventory inventory : resolveInventories(network)) {
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
        String key = StoredStack.itemKey(stack);
        for (Inventory inventory : resolveInventories(network)) {
            if (inventory.firstEmpty() >= 0) {
                return true;
            }
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()) {
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
