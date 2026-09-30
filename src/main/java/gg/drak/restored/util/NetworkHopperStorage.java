package gg.drak.restored.util;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkHopperRole;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.serialization.PersistedItemCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runtime processing and block configuration for linked network hopper chests. */
public final class NetworkHopperStorage {
    public static final int FILTER_SLOTS = 7;
    public static final int DEFAULT_MAX_STACK_SIZE = 64;
    private static final String FILTER_SEPARATOR = "\u0003";
    private static final String FILTER_KEY = "network-hopper-filter";
    private static final String MAX_STACK_KEY = "network-hopper-max-stack";

    private NetworkHopperStorage() {
    }

    public static boolean isHopper(Block block) {
        return role(block) != null;
    }

    public static NetworkHopperRole role(Block block) {
        if (block == null) {
            return null;
        }
        return NetworkBlockTags.getHopperRole(block)
                .map(NetworkHopperRole::fromId)
                .orElse(null);
    }

    public static Optional<java.util.UUID> getLinkedNetworkId(Block block) {
        return NetworkBlockTags.getLinkedNetworkId(block);
    }

    public static Network resolveNetwork(Block block) {
        return getLinkedNetworkId(block).map(NetworkManager::get).orElse(null);
    }

    /**
     * Processes both hopper roles using one link/inventory resolution pass. This is
     * intentionally the entry point used by the repeating task; resolving the same
     * block locations separately for inputs and outputs was needlessly expensive.
     */
    public static void process(Network network) {
        if (network == null || network.getLinkedHopperCount() <= 0) {
            return;
        }
        HopperInventories hoppers = resolveHopperInventories(network);
        if (hoppers.inputs().isEmpty() && hoppers.outputs().isEmpty()) {
            return;
        }
        processInputs(network, hoppers.inputs());
        processOutputs(network, hoppers.outputs());
    }

    public static void setRole(Block block, NetworkHopperRole role) {
        if (block != null && role != null) {
            NetworkBlockTags.setHopperRole(block, role.id());
        }
    }

    public static void link(Block block, Network network) {
        NetworkHopperRole role = role(block);
        if (role == null || network == null) {
            return;
        }
        network.addLinkedHopper(role, block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        NetworkBlockTags.setLinkedNetworkId(block, network.getIdentifier());
    }

    public static void unlink(Block block, Network network) {
        NetworkHopperRole role = role(block);
        if (role != null && network != null) {
            network.removeLinkedHopperKey(NetworkManager.locationKey(block.getLocation()));
        }
        NetworkBlockTags.clearLinkedNetworkId(block);
    }

    public static List<Inventory> resolveInventories(Network network, NetworkHopperRole role) {
        List<Inventory> result = new ArrayList<>();
        if (network == null || role == null) {
            return result;
        }
        HopperInventories hoppers = resolveHopperInventories(network);
        List<HopperInventory> selected = role == NetworkHopperRole.INPUT
                ? hoppers.inputs() : hoppers.outputs();
        for (HopperInventory hopper : selected) {
            if (!result.contains(hopper.inventory())) {
                result.add(hopper.inventory());
            }
        }
        return result;
    }

    public static void processInputs(Network network) {
        HopperInventories hoppers = resolveHopperInventories(network);
        processInputs(network, hoppers.inputs());
    }

    private static void processInputs(Network network, List<HopperInventory> hoppers) {
        for (HopperInventory hopper : hoppers) {
            Inventory inventory = hopper.inventory();
            ItemStack[] contents = inventory.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                long inserted = network.insert(stack, stack.getAmount());
                if (inserted <= 0) {
                    continue;
                }
                long remaining = stack.getAmount() - inserted;
                if (remaining <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    ItemStack left = stack.clone();
                    left.setAmount((int) remaining);
                    inventory.setItem(slot, left);
                }
            }
        }
    }

    public static void processOutputs(Network network) {
        HopperInventories hoppers = resolveHopperInventories(network);
        processOutputs(network, hoppers.outputs());
    }

    private static void processOutputs(Network network, List<HopperInventory> hoppers) {
        if (hoppers.isEmpty()) {
            return;
        }
        // Filters come from the tile snapshot resolveHopperInventories already took, decoded once
        // per distinct stored value; the item keys they need are known up front, so the network
        // only totals those instead of every item it holds.
        List<List<Filter>> filtersByHopper = new ArrayList<>(hoppers.size());
        Set<String> wanted = new HashSet<>();
        for (HopperInventory hopper : hoppers) {
            List<Filter> filters = cachedFilters(hopper.tile());
            filtersByHopper.add(filters);
            for (Filter filter : filters) {
                wanted.add(filter.itemKey());
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        Map<String, Long> availableAmounts = null;
        for (int index = 0; index < hoppers.size(); index++) {
            HopperInventory hopper = hoppers.get(index);
            List<Filter> filters = filtersByHopper.get(index);
            if (filters.isEmpty()) {
                continue;
            }
            Inventory inventory = hopper.inventory();
            int maxStack = getMaxStackSize(hopper.tile());
            for (Filter entry : filters) {
                ItemStack filter = entry.item();
                String itemKey = entry.itemKey();
                long current = countSimilar(inventory, filter);
                if (current >= maxStack) {
                    continue;
                }
                long room = Math.min(maxStack - current, availableCapacity(inventory, filter));
                if (availableAmounts == null) {
                    availableAmounts = network.getCombinedAmountsFor(wanted);
                }
                long amount = Math.min(room, availableAmounts.getOrDefault(itemKey, 0L));
                if (amount <= 0) {
                    continue;
                }
                long extracted = network.extract(itemKey, amount);
                if (extracted <= 0) {
                    continue;
                }
                ItemStack toAdd = filter.clone();
                toAdd.setAmount((int) Math.min(extracted, Integer.MAX_VALUE));
                java.util.Map<Integer, ItemStack> leftovers = inventory.addItem(toAdd);
                if (!leftovers.isEmpty()) {
                    long notAdded = leftovers.values().stream().mapToLong(ItemStack::getAmount).sum();
                    if (notAdded > 0) {
                        // Capacity was calculated before the insert; return a rare race remainder safely.
                        network.forceInsert(filter.clone(), notAdded);
                    }
                    extracted -= notAdded;
                }
                if (extracted > 0) {
                    availableAmounts.merge(itemKey, -extracted, Long::sum);
                }
                break;
            }
        }
    }

    private static HopperInventories resolveHopperInventories(Network network) {
        List<HopperInventory> inputs = new ArrayList<>();
        List<HopperInventory> outputs = new ArrayList<>();
        if (network == null) {
            return new HopperInventories(inputs, outputs);
        }

        List<String> dead = new ArrayList<>();
        for (String key : network.getLinkedHopperKeys()) {
            Location location = LinkedChestStorage.parseLocationKey(key);
            if (location == null || location.getWorld() == null) {
                continue;
            }
            // Skip unloaded chunks before any block access: getBlock() would force a
            // synchronous chunk load, and an unloaded hopper would read as absent and be
            // unlinked by the dead-link path below.
            if (!LinkedChestStorage.isChunkLoaded(location)) {
                continue;
            }
            Block block = location.getBlock();
            // One getState(false) read serves the role tag, the link tag, the filters, the max
            // stack and the inventory. It skips the tile-entity snapshot copy that getState()
            // makes (the bulk of this method's cost in profiles) and is only read from here.
            // TileState first, exactly as the old role(block) path tested it, so this cannot
            // reject a block the previous code accepted.
            if (!(block.getState(false) instanceof TileState tile)) {
                continue;
            }
            if (!(tile instanceof Container container)) {
                continue;
            }
            NetworkHopperRole actualRole =
                    NetworkHopperRole.fromId(NetworkBlockTags.getHopperRole(tile).orElse(null));
            if (actualRole == null
                    || !network.getIdentifier().equals(NetworkBlockTags.getLinkedNetworkId(tile).orElse(null))) {
                dead.add(key);
                continue;
            }
            HopperInventory hopper = new HopperInventory(block, tile, container.getInventory());
            List<HopperInventory> target = actualRole == NetworkHopperRole.INPUT ? inputs : outputs;
            boolean alreadyPresent = false;
            for (HopperInventory existing : target) {
                if (existing.inventory().equals(hopper.inventory())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                target.add(hopper);
            }
        }
        for (String key : dead) {
            network.removeLinkedHopperKey(key);
        }
        return new HopperInventories(inputs, outputs);
    }

    public static boolean hasStoredItems(Network network) {
        HopperInventories hoppers = resolveHopperInventories(network);
        for (HopperInventory hopper : hoppers.inputs()) {
            for (ItemStack stack : hopper.inventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    return true;
                }
            }
        }
        for (HopperInventory hopper : hoppers.outputs()) {
            for (ItemStack stack : hopper.inventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** See {@link LinkedChestStorage#hasUnresolvedLinks(Network)}. */
    public static boolean hasUnresolvedLinks(Network network) {
        if (network == null) {
            return false;
        }
        for (String key : network.getLinkedHopperKeys()) {
            if (LinkedChestStorage.parseLocationKey(key) == null) {
                return true;
            }
        }
        return false;
    }

    public static List<ItemStack> getFilters(Block block) {
        List<ItemStack> filters = emptyFilters();
        TileState tile = tile(block);
        if (tile == null) {
            return filters;
        }
        String raw = tile.getPersistentDataContainer().get(key(FILTER_KEY), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return filters;
        }
        return parseFilters(raw);
    }

    /** The {@link #FILTER_SLOTS} filter stacks encoded in {@code raw}; empty slots are null. */
    private static List<ItemStack> parseFilters(String raw) {
        List<ItemStack> filters = emptyFilters();
        String[] parts = raw.split(FILTER_SEPARATOR, -1);
        for (int i = 0; i < FILTER_SLOTS && i < parts.length; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            ItemStack decoded = PersistedItemCodec.deserializePayload(parts[i]);
            if (decoded != null && !decoded.getType().isAir() && decoded.getType() != Material.BARRIER) {
                decoded.setAmount(Math.max(1, Math.min(decoded.getMaxStackSize(), decoded.getAmount())));
                filters.set(i, decoded);
            }
        }
        return filters;
    }

    public static void setFilters(Block block, List<ItemStack> filters) {
        TileState tile = tile(block);
        if (tile == null) {
            return;
        }
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (i > 0) {
                value.append(FILTER_SEPARATOR);
            }
            ItemStack filter = filters != null && i < filters.size() ? filters.get(i) : null;
            if (filter != null && !filter.getType().isAir()) {
                ItemStack copy = filter.clone();
                copy.setAmount(Math.max(1, Math.min(copy.getMaxStackSize(), copy.getAmount())));
                value.append(PersistedItemCodec.serializePayload(copy));
            }
        }
        tile.getPersistentDataContainer().set(key(FILTER_KEY), PersistentDataType.STRING, value.toString());
        tile.update(true, false);
    }

    public static int getMaxStackSize(Block block) {
        return getMaxStackSize(tile(block));
    }

    private static int getMaxStackSize(TileState tile) {
        if (tile == null) {
            return DEFAULT_MAX_STACK_SIZE;
        }
        Integer value = tile.getPersistentDataContainer().get(key(MAX_STACK_KEY), PersistentDataType.INTEGER);
        return value == null ? DEFAULT_MAX_STACK_SIZE : clampMax(value);
    }

    public static void setMaxStackSize(Block block, int value) {
        TileState tile = tile(block);
        if (tile == null) {
            return;
        }
        tile.getPersistentDataContainer().set(key(MAX_STACK_KEY), PersistentDataType.INTEGER, clampMax(value));
        tile.update(true, false);
    }

    private static int clampMax(int value) {
        return Math.max(1, Math.min(64, value));
    }

    private static List<ItemStack> emptyFilters() {
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(null);
        }
        return filters;
    }

    private static TileState tile(Block block) {
        return block != null && block.getState() instanceof TileState tile ? tile : null;
    }

    private static org.bukkit.NamespacedKey key(String name) {
        return KEYS.computeIfAbsent(name, n -> new org.bukkit.NamespacedKey(gg.drak.restored.Restored.getInstance(), n));
    }

    private record HopperInventories(List<HopperInventory> inputs, List<HopperInventory> outputs) {
    }

    private record HopperInventory(Block block, TileState tile, Inventory inventory) {
    }

    /** A decoded output filter with its item key, computed once per stored filter value. */
    private record Filter(ItemStack item, String itemKey) {
    }

    /**
     * Decoded filters by their raw stored string. Keyed by the value itself, so a changed filter
     * simply misses the cache; bounded so retired values cannot pile up. Entries are shared and
     * must never be mutated.
     */
    private static final Map<String, List<Filter>> FILTER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int FILTER_CACHE_LIMIT = 256;
    private static final Map<String, org.bukkit.NamespacedKey> KEYS = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<Filter> cachedFilters(TileState tile) {
        String raw = tile.getPersistentDataContainer().get(key(FILTER_KEY), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Filter> cached = FILTER_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        List<Filter> parsed = new ArrayList<>();
        for (ItemStack filter : parseFilters(raw)) {
            if (filter != null && !filter.getType().isAir()) {
                parsed.add(new Filter(filter, StoredStack.itemKey(filter)));
            }
        }
        List<Filter> result = List.copyOf(parsed);
        if (FILTER_CACHE.size() >= FILTER_CACHE_LIMIT) {
            FILTER_CACHE.clear();
        }
        FILTER_CACHE.put(raw, result);
        return result;
    }

    private static long countSimilar(Inventory inventory, ItemStack template) {
        long total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && !stack.getType().isAir() && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static long availableCapacity(Inventory inventory, ItemStack template) {
        long capacity = 0;
        int max = Math.min(inventory.getMaxStackSize(), template.getMaxStackSize());
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                capacity += max;
            } else if (stack.isSimilar(template)) {
                capacity += Math.max(0, max - stack.getAmount());
            }
        }
        return capacity;
    }
}
