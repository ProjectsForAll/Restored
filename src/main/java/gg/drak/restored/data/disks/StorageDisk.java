package gg.drak.restored.data.disks;

import gg.drak.thebase.objects.Identifiable;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.Restored;
import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.screens.items.StoredItem;
import gg.drak.restored.database.dao.DiskDAO;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class StorageDisk implements Identifiable {
    private String identifier; // in UUID format
    private BigInteger capacity; // in items (not stacks)
    private ConcurrentSkipListSet<StoredItem> contents; // Map of ItemStacks to their quantity
    private Drive drive;
    private Integer slot;

    public UUID getUuid() {
        return UUID.fromString(identifier);
    }

    public StorageDisk(Drive drive, String identifier) {
        this.drive = drive;
        this.identifier = identifier;

        buildOrGetSettings();
    }

    public StorageDisk(Drive drive, String identifier, int slot) {
        this.drive = drive;
        this.identifier = identifier;
        this.slot = slot;

        buildOrGetSettings();
    }

    public void buildOrGetSettings() {
        Optional<DiskDAO.DiskData> dataOpt = Restored.getDatabase().getDiskDAO().getById(identifier);

        if (dataOpt.isPresent()) {
            DiskDAO.DiskData data = dataOpt.get();
            this.capacity = data.getCapacity();
            this.contents = data.getItems();
        } else {
            // Try to get capacity from disk item if it exists
            // Default capacity if not found
            this.capacity = BigInteger.valueOf(1024);
            this.contents = new ConcurrentSkipListSet<>();
            save();
        }
    }

    public void save() {
        // Cache in middleware immediately
        Restored.getDatabase().getMiddleware().cacheDisk(this);

        String driveId = drive != null ? drive.getIdentifier() : null;
        Restored.getDatabase().getDiskDAO().insert(identifier, driveId, slot, capacity, contents);
    }

    public StoredItem get(String uuid) {
        AtomicReference<Optional<StoredItem>> item = new AtomicReference<>(Optional.empty());

        contents.forEach(i -> {
            if (i.getUuid().toString().equals(uuid)) {
                item.set(Optional.of(i));
            }
        });

        return item.get().orElse(null);
    }

    public boolean contains(StoredItem item) {
        return get(item.getUuid().toString()) != null;
    }

    public Optional<StoredItem> getStoredItem(ItemStack stack) {
        for (StoredItem item : contents) {
            if (item.isComparable(stack)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public BigInteger getQuantity(ItemStack stack) {
        Optional<StoredItem> item = getStoredItem(stack);

        if (item.isPresent()) {
            return item.get().getAmount();
        }

        return BigInteger.ZERO;
    }

    public StoreResult storeItems(ItemStack stack) {
        Material material = stack.getType();
        int quantity = stack.getAmount();

        if (isFull()) {
            return new StoreResult(this, stack, BigInteger.ZERO);
        }

        BigInteger leftover = addItem(stack);

        return new StoreResult(this, stack, leftover);
    }

    /**
     * Add an item to the storage disk.
     * @return The leftover quantity that could not be added.
     */
    public BigInteger addItem(ItemStack stack) {
        if (isFull()) return BigInteger.valueOf(stack.getAmount());

        Optional<StoredItem> existing = getStoredItem(stack);
        BigInteger itemQuantity = existing.map(StoredItem::getAmount).orElse(BigInteger.ZERO);

        if (! hasSpaceFor(stack)) {
            BigInteger canAdd = getRemainingCapacity();
            if (canAdd.compareTo(BigInteger.ZERO) <= 0) return BigInteger.valueOf(stack.getAmount());

            existing.ifPresent(contents::remove);
            contents.add(new StoredItem(existing.map(StoredItem::getIdentifier).orElse(UUID.randomUUID().toString()), itemQuantity.add(canAdd), stack));

            return BigInteger.valueOf(stack.getAmount()).subtract(canAdd);
        }

        existing.ifPresent(contents::remove);
        contents.add(new StoredItem(existing.map(StoredItem::getIdentifier).orElse(UUID.randomUUID().toString()), itemQuantity.add(BigInteger.valueOf(stack.getAmount())), stack));

        return BigInteger.ZERO;
    }

    public BigInteger getRemainingCapacity() {
        return getCapacity().subtract(getTotalQuantity());
    }

    public boolean hasSpaceFor(ItemStack stack) {
        if (isFull()) return false;

        BigInteger quantity = BigInteger.valueOf(stack.getAmount());

        return getRemainingCapacity().compareTo(quantity) >= 0;
    }

    public boolean isFull() {
        // check if total quantity is greater than or equal to capacity
        return getTotalQuantity().compareTo(getCapacity()) >= 0;
    }

    public BigInteger getTotalQuantity() {
        BigInteger total = BigInteger.ZERO;

        for (StoredItem item : contents) {
            total = total.add(item.getAmount());
        }

        return total;
    }

    public ItemStack getItem() {
        ItemStack stack = new ItemStack(Material.IRON_INGOT);
        stack.setAmount(1);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorizeHard("&cStorage Disk"));
            meta.setLore(getLore());

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(getStorageKey("storage-disk"), PersistentDataType.STRING, getUuid().toString());

            stack.setItemMeta(meta);
        }

        return stack;
    }

    public NamespacedKey getStorageKey(String key) {
        return new NamespacedKey(Restored.getInstance(), key);
    }

    public List<String> getLore() {
        List<String> lore = new ArrayList<>();

        lore.add(ColorUtils.colorizeHard("&eCapacity&7: &a" + getCapacity()));
        lore.add(ColorUtils.colorizeHard("&eCurrently Held&7: &a" + getTotalQuantity()));
        lore.add(ColorUtils.colorizeHard("&eSpace Left&7: &a" + getRemainingCapacity()));

        lore.add(ColorUtils.colorizeHard("&r"));

        lore.add(ColorUtils.colorizeHard("&eDisk ID&7: &c" + getUuid()));

        return lore;
    }

    public void removeItem(StoredItem storedItem, BigInteger amount) {
        if (! contains(storedItem)) return;
        StoredItem real = get(storedItem.getIdentifier());

        BigInteger leftover = real.getAmount().subtract(amount);

        contents.remove(real);
        if (leftover.compareTo(BigInteger.ZERO) > 0) {
            contents.add(new StoredItem(real.getIdentifier(), leftover, real.getItem()));
        }
    }

    @Getter @Setter
    public static class StorageResult {
        private StorageDisk storageDisk;

        public StorageResult(StorageDisk storageDisk) {
            this.storageDisk = storageDisk;
        }
    }

    @Getter @Setter
    public static class StoreResult extends StorageResult {
        private ItemStack attemptedStack;
        private BigInteger leftover;

        public StoreResult(StorageDisk storageDisk, ItemStack attemptedStack, BigInteger leftover) {
            super(storageDisk);

            this.attemptedStack = attemptedStack;
            this.leftover = leftover;
        }

        public boolean storedSome() {
            // if leftover is less than the attempted stack amount, then we stored some
            return leftover.compareTo(BigInteger.valueOf(attemptedStack.getAmount())) < 0;
        }

        public boolean storedAll() {
            // if leftover is equal to or less than 0, then we stored all
            return leftover.compareTo(BigInteger.ZERO) <= 0;
        }

        public BigInteger getRemainingCapacity() {
            return getStorageDisk().getRemainingCapacity();
        }

        public boolean hasRemainingCapacity() {
            return getRemainingCapacity().compareTo(BigInteger.ZERO) > 0;
        }

        public BigInteger getTotalOfMaterial() {
            return getStorageDisk().getQuantity(attemptedStack);
        }
    }
}
