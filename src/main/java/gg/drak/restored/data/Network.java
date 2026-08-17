package gg.drak.restored.data;

import gg.drak.restored.Restored;
import gg.drak.restored.serialization.PersistedItemCodec;
import gg.drak.restored.util.LinkedChestStorage;
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
    /** Runtime workstation GUI slot state — not DB-persisted. */
    @Getter(AccessLevel.NONE)
    private final ConcurrentHashMap<AugmentType, WorkstationSession> workstationSessions = new ConcurrentHashMap<>();
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
        return new Location(Bukkit.getWorld(world), x, y, z);
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
        long total = 0;
        for (StoredStack stack : items.values()) {
            total += stack.getAmount();
        }
        return total;
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
        if (isOwner(playerUuid)) {
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

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid.equals(playerUuid);
    }

    public boolean canAccess(UUID playerUuid) {
        return isOwner(playerUuid) || getRole(playerUuid).canAccess();
    }

    public boolean canDeposit(UUID playerUuid) {
        return isOwner(playerUuid) || getRole(playerUuid).canDeposit();
    }

    public boolean canWithdraw(UUID playerUuid) {
        return isOwner(playerUuid) || getRole(playerUuid).canWithdraw();
    }

    public boolean canManage(UUID playerUuid) {
        return isOwner(playerUuid) || getRole(playerUuid).canManage();
    }

    public boolean canUseAugments(UUID playerUuid) {
        return isOwner(playerUuid) || getRole(playerUuid).canUseAugments();
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

    public boolean hasLinkedChestKey(String locationKey) {
        return locationKey != null && linkedChestKeys.contains(locationKey);
    }

    public boolean addLinkedChest(String world, int x, int y, int z) {
        if (world == null || world.isEmpty()) {
            return false;
        }
        boolean added = linkedChestKeys.add(NetworkManager.locationKey(world, x, y, z));
        if (added) {
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
            markDirty();
        }
        return removed;
    }

    public void loadLinkedChest(String world, int x, int y, int z) {
        if (world != null && !world.isEmpty()) {
            linkedChestKeys.add(NetworkManager.locationKey(world, x, y, z));
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
        if (!items.isEmpty()) {
            return false;
        }
        if (upgradeCount > 0 || enchantingBookshelves > 0 || !installedAugments.isEmpty()) {
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
        markDirty();
        return amount;
    }

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
        markDirty();
        return linkedTaken + taken;
    }

    /**
     * Merged view of virtual storage plus live linked-chest contents (by item key).
     */
    public List<StoredStack> getCombinedStacks() {
        Map<String, StoredStack> combined = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, StoredStack> entry : items.entrySet()) {
            StoredStack stack = entry.getValue();
            combined.put(entry.getKey(), new StoredStack(stack.getTemplate(), stack.getAmount()));
        }
        for (Map.Entry<String, StoredStack> entry : LinkedChestStorage.aggregateLinkedByKey(this).entrySet()) {
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

    public long getCombinedAmount(String itemKey) {
        if (itemKey == null) {
            return 0;
        }
        long total = 0;
        StoredStack virtual = items.get(itemKey);
        if (virtual != null) {
            total += virtual.getAmount();
        }
        total += LinkedChestStorage.extractableAmount(this, itemKey);
        return total;
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
        linkedChestKeys.clear();
        NetworkManager.unregister(this);
        Restored.getDatabase().getMiddleware().queueNetworkDelete(this);
    }

    public void loadItem(String itemKey, String itemData, long amount) {
        ItemStack stack = PersistedItemCodec.deserializePayload(itemData);
        // Recompute identity from the actual stack so withdraw/deposit keys match after codec changes.
        String key = StoredStack.itemKey(stack);
        StoredStack existing = items.get(key);
        if (existing != null) {
            existing.setAmount(existing.getAmount() + amount);
        } else {
            items.put(key, new StoredStack(stack, amount));
        }
        if (!key.equals(itemKey)) {
            markDirty();
        }
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
