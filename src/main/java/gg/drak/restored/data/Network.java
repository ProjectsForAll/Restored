package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.serialization.PersistedItemCodec;
import gg.drak.restored.util.LinkedChestCache;
import gg.drak.restored.util.LinkedChestStorage;
import gg.drak.restored.util.NetworkHopperStorage;
import gg.drak.restored.util.NetworkBlockTags;
import host.plas.bou.gui.items.ItemData;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Getter @Setter
public class Network {
    private static final Comparator<StoredStack> STORED_STACK_COMPARATOR = new StoredStackComparator();

    private final UUID identifier;
    private UUID ownerUuid;
    private String world;
    private int x;
    private int y;
    private int z;
    private int upgradeCount;
    private int enchantingBookshelves;
    private final ConcurrentHashMap<String, StoredStack> items = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, NetworkRole> roles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> openCounts = new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE)
    private final Set<AugmentType> installedAugments = ConcurrentHashMap.newKeySet();
    /** Location keys (`world:x:y:z`) of linked vanilla storage chests. */
    @Getter(AccessLevel.NONE)
    private final Set<String> linkedChestKeys = ConcurrentHashMap.newKeySet();
    /** Location keys of linked input/output network hopper chests. */
    @Getter(AccessLevel.NONE)
    private final Set<String> linkedHopperKeys = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, NetworkHopperRole> linkedHopperRoles = new ConcurrentHashMap<>();
    /** Runtime workstation GUI slot state — not DB-persisted. */
    @Getter(AccessLevel.NONE)
    private final ConcurrentHashMap<AugmentType, WorkstationSession> workstationSessions = new ConcurrentHashMap<>();
    /** Persisted automatic compactor rules. */
    @Getter(AccessLevel.NONE)
    private final ConcurrentHashMap<UUID, CompactConfiguration> compactConfigurations = new ConcurrentHashMap<>();
    /** Stored items whose payload cannot be decoded on this server; see {@link #loadItem}. */
    @Getter(AccessLevel.NONE)
    private final ConcurrentHashMap<String, UnresolvedItem> unresolvedItems = new ConcurrentHashMap<>();
    /** Virtual-storage item total, maintained alongside the item map for hot capacity checks. */
    private final AtomicLong totalItems = new AtomicLong();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public Network(UUID identifier, UUID ownerUuid) {
        this.identifier = identifier;
        this.ownerUuid = ownerUuid;
    }

    public String getIdentifierString() {
        return identifier.toString();
    }

    public int getCapacity() {
        return 64 * upgradeCount;
    }

    public boolean isPlaced() {
        return world != null && !world.isEmpty();
    }

    public Location getLocation() {
        if (!isPlaced()) {
            return null;
        }
        org.bukkit.World loadedWorld = Bukkit.getWorld(world);
        return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            this.world = null;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        } else {
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }
        markDirty();
    }

    public void clearLocation() {
        setLocation(null);
    }

    public long getTotalItems() {
        return totalItems.get();
    }

    public int getDifferedItemCount() {
        return items.size();
    }

    public long getTotalOpens() {
        long total = 0;
        for (Long opens : openCounts.values()) {
            total += opens;
        }
        return total;
    }

    public void recordOpen(UUID playerUuid) {
        Long current = openCounts.get(playerUuid);
        openCounts.put(playerUuid, current == null ? 1L : current + 1L);
        markDirty();
    }

    public NetworkRole getRole(UUID playerUuid) {
        if (actsAsOwner(playerUuid)) {
            return NetworkRole.ADMIN;
        }
        return roles.getOrDefault(playerUuid, NetworkRole.BLOCKED);
    }

    public void setRole(UUID playerUuid, NetworkRole role) {
        if (isOwner(playerUuid)) {
            return;
        }
        if (role == null || role == NetworkRole.BLOCKED) {
            roles.remove(playerUuid);
        } else {
            roles.put(playerUuid, role);
        }
        markDirty();
    }

    /** The recorded owner only; see {@link #actsAsOwner} for access decisions. */
    public boolean isOwner(UUID playerUuid) {
        return ownerUuid.equals(playerUuid);
    }

    /**
     * True for the owner and for a server admin with owner-level access to this network (admin
     * mode, or opened from the admin network list). Use this for permission checks; use
     * {@link #isOwner} only where the recorded owner itself matters.
     */
    public boolean actsAsOwner(UUID playerUuid) {
        return isOwner(playerUuid) || AdminAccess.actsAsOwner(playerUuid, identifier);
    }

    public boolean canAccess(UUID playerUuid) {
        return actsAsOwner(playerUuid) || getRole(playerUuid).canAccess();
    }

    public boolean canDeposit(UUID playerUuid) {
        return actsAsOwner(playerUuid) || getRole(playerUuid).canDeposit();
    }

    public boolean canWithdraw(UUID playerUuid) {
        return actsAsOwner(playerUuid) || getRole(playerUuid).canWithdraw();
    }

    public boolean canManage(UUID playerUuid) {
        return actsAsOwner(playerUuid) || getRole(playerUuid).canManage();
    }

    public boolean canUseAugments(UUID playerUuid) {
        return actsAsOwner(playerUuid) || getRole(playerUuid).canUseAugments();
    }

    public boolean hasAugment(AugmentType type) {
        return type != null && installedAugments.contains(type);
    }

    public Set<AugmentType> getInstalledAugments() {
        if (installedAugments.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(installedAugments));
    }

    /**
     * @return true if newly installed
     */
    public boolean installAugment(AugmentType type) {
        if (type == null || installedAugments.contains(type)) {
            return false;
        }
        installedAugments.add(type);
        markDirty();
        return true;
    }

    /**
     * @return true if an augment was removed
     */
    public boolean uninstallAugment(AugmentType type) {
        if (type == null || !installedAugments.remove(type)) {
            return false;
        }
        if (type == AugmentType.ENCHANTING && enchantingBookshelves > 0) {
            getOrCreateWorkstationSession(type).setEnchantingBookshelves(enchantingBookshelves);
        }
        WorkstationSession session = workstationSessions.remove(type);
        if (session != null) {
            for (ItemStack stack : session.drainAllItems()) {
                forceInsert(stack, stack.getAmount());
            }
        }
        if (type == AugmentType.ENCHANTING) {
            enchantingBookshelves = 0;
        }
        if (type == AugmentType.COMPACTOR) {
            compactConfigurations.clear();
        }
        markDirty();
        return true;
    }

    public WorkstationSession getOrCreateWorkstationSession(AugmentType type) {
        if (type == null) {
            return new WorkstationSession();
        }
        return workstationSessions.computeIfAbsent(type, t -> new WorkstationSession());
    }

    public WorkstationSession getWorkstationSession(AugmentType type) {
        return type == null ? null : workstationSessions.get(type);
    }

    public void loadAugment(AugmentType type) {
        if (type != null) {
            installedAugments.add(type);
        }
    }

    public List<CompactConfiguration> getCompactConfigurations() {
        List<CompactConfiguration> result = new ArrayList<>();
        for (CompactConfiguration configuration : compactConfigurations.values()) {
            result.add(configuration);
        }
        result.sort(Comparator.comparing(configuration -> configuration.getIdentifier().toString()));
        return Collections.unmodifiableList(result);
    }

    /** Alias matching the terminology used by the compactor GUI. */
    public List<CompactConfiguration> getCompactingConfigurations() {
        return getCompactConfigurations();
    }

    public CompactConfiguration getCompactConfiguration(UUID identifier) {
        return identifier == null ? null : compactConfigurations.get(identifier);
    }

    public CompactConfiguration createCompactConfiguration() {
        CompactConfiguration configuration = new CompactConfiguration(UUID.randomUUID());
        compactConfigurations.put(configuration.getIdentifier(), configuration);
        markDirty();
        return configuration;
    }

    public void loadCompactConfiguration(CompactConfiguration configuration) {
        if (configuration != null) {
            compactConfigurations.put(configuration.getIdentifier(), configuration);
        }
    }

    public boolean removeCompactConfiguration(UUID identifier) {
        if (identifier == null || compactConfigurations.remove(identifier) == null) {
            return false;
        }
        markDirty();
        return true;
    }

    public void clearCompactConfigurations() {
        if (!compactConfigurations.isEmpty()) {
            compactConfigurations.clear();
            markDirty();
        }
    }

    public void loadEnchantingBookshelves(int count) {
        enchantingBookshelves = Math.max(0, Math.min(15, count));
    }

    public void setEnchantingBookshelves(int count) {
        int clamped = Math.max(0, Math.min(15, count));
        if (enchantingBookshelves != clamped) {
            enchantingBookshelves = clamped;
            markDirty();
        }
    }

    public Set<String> getLinkedChestKeys() {
        if (linkedChestKeys.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(linkedChestKeys);
    }

    public int getLinkedChestCount() {
        return linkedChestKeys.size();
    }

    public Set<String> getLinkedHopperKeys() {
        if (linkedHopperKeys.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(linkedHopperKeys);
    }

    public int getLinkedHopperCount() {
        return linkedHopperKeys.size();
    }

    public boolean hasLinkedHopperKey(String locationKey) {
        return locationKey != null && linkedHopperKeys.contains(locationKey);
    }

    public boolean addLinkedHopper(NetworkHopperRole role, String world, int x, int y, int z) {
        if (role == null || world == null || world.isEmpty()) {
            return false;
        }
        boolean added = linkedHopperKeys.add(NetworkManager.locationKey(world, x, y, z));
        linkedHopperRoles.put(NetworkManager.locationKey(world, x, y, z), role);
        if (added) {
            markDirty();
        }
        return added;
    }

    public void loadLinkedHopper(String world, int x, int y, int z) {
        loadLinkedHopper(world, x, y, z, null);
    }

    public void loadLinkedHopper(String world, int x, int y, int z, NetworkHopperRole role) {
        if (world != null && !world.isEmpty()) {
            String key = NetworkManager.locationKey(world, x, y, z);
            linkedHopperKeys.add(key);
            if (role != null) {
                linkedHopperRoles.put(key, role);
            }
        }
    }

    public NetworkHopperRole getLinkedHopperRole(String locationKey) {
        return linkedHopperRoles.get(locationKey);
    }

    public boolean removeLinkedHopperKey(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return false;
        }
        boolean removed = linkedHopperKeys.remove(locationKey);
        linkedHopperRoles.remove(locationKey);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void clearLinkedHopperTags() {
        for (String key : linkedHopperKeys) {
            Location location = LinkedChestStorage.parseLocationKey(key);
            if (location != null && location.getWorld() != null) {
                NetworkBlockTags.clearLinkedNetworkId(location.getBlock());
            }
        }
    }

    public boolean hasLinkedChestKey(String locationKey) {
        return locationKey != null && linkedChestKeys.contains(locationKey);
    }

    public boolean addLinkedChest(String world, int x, int y, int z) {
        if (world == null || world.isEmpty()) {
            return false;
        }
        String key = NetworkManager.locationKey(world, x, y, z);
        boolean added = linkedChestKeys.add(key);
        if (added) {
            LinkedChestCache.track(key);
            markDirty();
        }
        return added;
    }

    public boolean removeLinkedChest(String world, int x, int y, int z) {
        if (world == null || world.isEmpty()) {
            return false;
        }
        return removeLinkedChestKey(NetworkManager.locationKey(world, x, y, z));
    }

    public boolean removeLinkedChestKey(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return false;
        }
        boolean removed = linkedChestKeys.remove(locationKey);
        if (removed) {
            LinkedChestCache.untrack(locationKey);
            markDirty();
        }
        return removed;
    }

    public void loadLinkedChest(String world, int x, int y, int z) {
        if (world != null && !world.isEmpty()) {
            String key = NetworkManager.locationKey(world, x, y, z);
            if (linkedChestKeys.add(key)) {
                LinkedChestCache.track(key);
            }
        }
    }

    public void clearLinkedChestTags() {
        for (String key : linkedChestKeys) {
            Location location = LinkedChestStorage.parseLocationKey(key);
            if (location != null && location.getWorld() != null) {
                Block block = location.getBlock();
                NetworkBlockTags.clearLinkedNetworkId(block);
            }
        }
    }

    public boolean isEmpty() {
        if (!items.isEmpty() || !unresolvedItems.isEmpty()) {
            return false;
        }
        if (upgradeCount > 0 || enchantingBookshelves > 0 || !installedAugments.isEmpty()) {
            return false;
        }
        if (NetworkHopperStorage.hasUnresolvedLinks(this) || NetworkHopperStorage.hasStoredItems(this)) {
            return false;
        }
        if (LinkedChestStorage.hasUnresolvedLinks(this) || LinkedChestStorage.countLinkedItems(this) > 0) {
            return false;
        }
        for (WorkstationSession session : workstationSessions.values()) {
            if (session != null && !session.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean canInsert(ItemStack stack, long amount) {
        if (stack == null || stack.getType().isAir() || amount <= 0) {
            return false;
        }
        if (getTotalItems() < getCapacity()) {
            return true;
        }
        return LinkedChestStorage.hasLinkedSpace(this, stack);
    }

    /** Fills linked chests first, then virtual storage up to its capacity. */
    public long insert(ItemStack stack, long amount) {
        if (stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        long linkedInserted = LinkedChestStorage.insertIntoLinked(this, stack, amount);
        long remaining = amount - linkedInserted;
        if (remaining <= 0) {
            return linkedInserted;
        }
        long space = getCapacity() - getTotalItems();
        if (space <= 0) {
            return linkedInserted;
        }
        long toInsert = Math.min(remaining, space);
        String key = StoredStack.itemKey(stack);
        StoredStack existing = items.get(key);
        if (existing == null) {
            items.put(key, new StoredStack(stack, toInsert));
        } else {
            existing.setAmount(existing.getAmount() + toInsert);
        }
        totalItems.addAndGet(toInsert);
        markDirty();
        return linkedInserted + toInsert;
    }

    /** Insert ignoring capacity — used when reclaiming GUI session items on uninstall. */
    public long forceInsert(ItemStack stack, long amount) {
        if (stack == null || stack.getType().isAir() || amount <= 0) {
            return 0;
        }
        String key = StoredStack.itemKey(stack);
        StoredStack existing = items.get(key);
        if (existing == null) {
            items.put(key, new StoredStack(stack, amount));
        } else {
            existing.setAmount(existing.getAmount() + amount);
        }
        totalItems.addAndGet(amount);
        markDirty();
        return amount;
    }

    /** Takes from linked chests first, then from virtual storage. */
    public long extract(String itemKey, long amount) {
        if (itemKey == null || amount <= 0) {
            return 0;
        }
        long linkedTaken = LinkedChestStorage.extractFromLinked(this, itemKey, amount);
        long remaining = amount - linkedTaken;
        if (remaining <= 0) {
            return linkedTaken;
        }
        StoredStack stack = items.get(itemKey);
        if (stack == null) {
            return linkedTaken;
        }
        long taken = Math.min(remaining, stack.getAmount());
        long left = stack.getAmount() - taken;
        if (left <= 0) {
            items.remove(itemKey);
        } else {
            stack.setAmount(left);
        }
        totalItems.addAndGet(-taken);
        markDirty();
        return linkedTaken + taken;
    }

    /**
     * Merged view of virtual storage plus linked-chest contents (by item key). Linked chests in
     * unloaded chunks contribute their last known contents.
     */
    public List<StoredStack> getCombinedStacks() {
        return getCombinedStacks(null);
    }

    /**
     * @param accept optional Material test. Periodic augment processors that consume only one
     *               family of items (arrows, food, rockets) pass it to skip everything else.
     *               Virtual storage is filtered too, so callers see exactly the stacks they keep.
     */
    public List<StoredStack> getCombinedStacks(java.util.function.Predicate<org.bukkit.Material> accept) {
        Map<String, StoredStack> combined = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, StoredStack> entry : items.entrySet()) {
            StoredStack stack = entry.getValue();
            if (accept != null && (stack.getTemplate() == null || !accept.test(stack.getTemplate().getType()))) {
                continue;
            }
            StoredStack copy = new StoredStack(stack.getTemplate(), stack.getAmount());
            copy.setCachedItemKey(entry.getKey());
            combined.put(entry.getKey(), copy);
        }
        for (Map.Entry<String, StoredStack> entry
                : LinkedChestStorage.aggregateLinkedByKey(this, accept).entrySet()) {
            StoredStack linked = entry.getValue();
            StoredStack existing = combined.get(entry.getKey());
            if (existing == null) {
                combined.put(entry.getKey(), linked);
            } else {
                existing.setAmount(existing.getAmount() + linked.getAmount());
            }
        }
        return new ArrayList<>(combined.values());
    }

    /**
     * Amount of {@code itemKey} that {@link #extract} can currently reach: virtual storage plus
     * linked chests that are loaded.
     */
    public long getCombinedAmount(String itemKey) {
        if (itemKey == null) {
            return 0;
        }
        long total = 0;
        StoredStack virtual = items.get(itemKey);
        if (virtual != null) {
            total += virtual.getAmount();
        }
        return total + LinkedChestStorage.extractableAmount(this, itemKey);
    }

    /**
     * Item key → amount {@link #extract} can currently reach, for callers checking several keys
     * at once.
     */
    public Map<String, Long> getCombinedAmounts() {
        Map<String, Long> amounts = new java.util.HashMap<>();
        for (Map.Entry<String, StoredStack> entry : items.entrySet()) {
            amounts.merge(entry.getKey(), entry.getValue().getAmount(), Long::sum);
        }
        for (Map.Entry<String, Long> entry : LinkedChestStorage.linkedAmounts(this).entrySet()) {
            amounts.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return amounts;
    }

    /** {@link #getCombinedAmounts()} restricted to {@code itemKeys}, without merging everything else. */
    public Map<String, Long> getCombinedAmountsFor(java.util.Collection<String> itemKeys) {
        Map<String, Long> amounts = new java.util.HashMap<>();
        for (String itemKey : itemKeys) {
            StoredStack stored = items.get(itemKey);
            if (stored != null) {
                amounts.put(itemKey, stored.getAmount());
            }
        }
        for (Map.Entry<String, Long> entry : LinkedChestStorage.linkedAmountsFor(this, itemKeys).entrySet()) {
            amounts.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return amounts;
    }

    public ItemStack getCombinedTemplate(String itemKey) {
        if (itemKey == null) {
            return null;
        }
        StoredStack virtual = items.get(itemKey);
        if (virtual != null) {
            return virtual.getTemplate();
        }
        return LinkedChestStorage.findTemplate(this, itemKey);
    }

    public List<StoredStack> getPage(int page, int perPage) {
        List<StoredStack> sorted = new ArrayList<>(items.values());
        sorted.sort(STORED_STACK_COMPARATOR);
        int start = page * perPage;
        if (start >= sorted.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + perPage, sorted.size());
        return sorted.subList(start, end);
    }

    public int getPageCount(int perPage) {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(items.size() / (double) perPage));
    }

    public void addUpgrade() {
        upgradeCount++;
        markDirty();
    }

    /**
     * Removes one upgrade if capacity would still fit stored items.
     * @return true if an upgrade was removed
     */
    public boolean removeUpgrade() {
        if (upgradeCount <= 0) {
            return false;
        }
        int newCapacity = 64 * (upgradeCount - 1);
        if (getTotalItems() > newCapacity) {
            return false;
        }
        upgradeCount--;
        markDirty();
        return true;
    }

    public void transferOwnership(UUID newOwner) {
        if (newOwner == null) {
            return;
        }
        roles.remove(newOwner);
        this.ownerUuid = newOwner;
        markDirty();
    }

    public void markDirty() {
        dirty.set(true);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Queue an async DB save. Memory/cache is already the source of truth;
     * this only schedules persistence and must stay cheap on the main thread.
     */
    public void save() {
        Restored.getDatabase().getMiddleware().queueNetworkSave(this);
    }

    /**
     * Unregister from runtime caches and queue async DB deletion.
     */
    public void delete() {
        clearLinkedChestTags();
        clearLinkedHopperTags();
        for (String key : linkedChestKeys) {
            LinkedChestCache.untrack(key);
        }
        linkedChestKeys.clear();
        linkedHopperKeys.clear();
        linkedHopperRoles.clear();
        NetworkManager.unregister(this);
        Restored.getDatabase().getMiddleware().queueNetworkDelete(this);
    }

    public void loadItem(String itemKey, String itemData, long amount) {
        ItemStack stack = PersistedItemCodec.tryDeserializePayload(itemData);
        if (stack == null) {
            // Kept byte-for-byte and written back unchanged, so the item returns intact once
            // whatever it depends on (usually a datapack) is available again. It still occupies
            // capacity, but is not listed or withdrawable meanwhile.
            unresolvedItems.merge(itemKey, new UnresolvedItem(itemKey, itemData, amount),
                    (a, b) -> new UnresolvedItem(a.itemKey(), a.itemData(), a.amount() + b.amount()));
            totalItems.addAndGet(amount);
            return;
        }
        // Recompute identity from the actual stack so withdraw/deposit keys match after codec changes.
        String key = StoredStack.itemKey(stack);
        StoredStack existing = items.get(key);
        if (existing != null) {
            existing.setAmount(existing.getAmount() + amount);
        } else {
            items.put(key, new StoredStack(stack, amount));
        }
        totalItems.addAndGet(amount);
        if (!key.equals(itemKey)) {
            markDirty();
        }
    }

    /** A stored item this server cannot currently decode, preserved as its raw payload. */
    public record UnresolvedItem(String itemKey, String itemData, long amount) {
    }

    public java.util.Collection<UnresolvedItem> getUnresolvedItems() {
        return Collections.unmodifiableCollection(unresolvedItems.values());
    }

    public ItemData toItemData(String itemKey, StoredStack stack) {
        return new ItemData(itemKey, BigInteger.valueOf(stack.getAmount()), PersistedItemCodec.serializePayload(stack.getTemplate()));
    }

    public String getOwnerName() {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        return owner.getName() != null ? owner.getName() : ownerUuid.toString();
    }

    private static final class StoredStackComparator implements Comparator<StoredStack> {
        @Override
        public int compare(StoredStack a, StoredStack b) {
            return a.itemKey().compareTo(b.itemKey());
        }
    }
}
