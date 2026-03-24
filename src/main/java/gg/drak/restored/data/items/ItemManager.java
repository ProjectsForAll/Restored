package gg.drak.restored.data.items;

import gg.drak.restored.Restored;
import gg.drak.restored.data.items.impl.*;
import gg.drak.restored.data.items.impl.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ItemManager {
    public static String deserializeType(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (! container.has(RestoredItem.getTypeKey())) {
            return null;
        }

        return container.get(RestoredItem.getTypeKey(), PersistentDataType.STRING);
    }

    public static ItemType getTypeFrom(ItemStack stack) {
        String type = deserializeType(stack);
        if (type == null) {
            return ItemType.NONE;
        }

        try {
            return ItemType.valueOf(type);
        } catch (Exception e) {
            Restored.getInstance().logWarning("Error while deserializing item type: " + type, e);

            return ItemType.NONE;
        }
    }

    public static RestoredItem readItemHard(ItemStack stack) {
        ItemType type = getTypeFrom(stack);
        switch (type) {
            case CONTROLLER:
                return new ControllerItem();
            case DRIVE:
                return new DriveItem();
            case VIEWER:
                return new ViewerItem();
            case CRAFTING_VIEWER:
                return new CraftingViewerItem();
            case GENERIC_DISK:
                Optional<BigInteger> capacity = readCapacity(stack);
                Optional<String> identifier = readDiskIdentifier(stack);
                return new GenericDiskItem(ItemType.GENERIC_DISK, capacity.orElse(BigInteger.valueOf(1000)), identifier.orElse(UUID.randomUUID().toString()));
            case FOUR_K_DISK:
                Optional<String> fourKIdentifier = readDiskIdentifier(stack);
                return new FourKDiskItem(fourKIdentifier.orElse(UUID.randomUUID().toString()));
            default:
                return null;
        }
    }

    public static Optional<RestoredItem> readItem(ItemStack stack) {
        return Optional.ofNullable(readItemHard(stack));
    }

    public static Optional<String> readDiskIdentifier(ItemStack stack) {
        Optional<String> id = readString(GenericDiskItem.IDENTIFIER_KEY, stack);
        if (id.isPresent()) return id;
        // Legacy stacks from older StorageDisk.getItem() used this key only
        return readString("storage-disk", stack);
    }

    public static Optional<BigInteger> readCapacity(ItemStack stack) {
        Optional<String> capacity = readString(GenericDiskItem.CAPACITY_KEY, stack);
        return capacity.map(BigInteger::new);
    }

    public static Optional<String> readString(String key, ItemStack stack) {
        NamespacedKey namespacedKey = getNamedKey(key);

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return Optional.empty();

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (! container.has(namespacedKey, PersistentDataType.STRING)) return Optional.empty();

        return Optional.ofNullable(container.get(namespacedKey, PersistentDataType.STRING));
    }

    public static NamespacedKey getNamedKey(String key) {
        return new NamespacedKey(Restored.getInstance(), key);
    }

    public static Optional<RestoredItem> getItem(String item, String... args) {
        try {
            ItemType type = ItemType.valueOf(item);

            switch (type) {
                case CONTROLLER:
                    return Optional.of(new ControllerItem());
                case DRIVE:
                    return Optional.of(new DriveItem());
                case VIEWER:
                    return Optional.of(new ViewerItem());
                case CRAFTING_VIEWER:
                    return Optional.of(new CraftingViewerItem());
                case GENERIC_DISK:
                    if (args.length == 2) {
                        return Optional.of(new GenericDiskItem(ItemType.GENERIC_DISK, new BigInteger(args[0]), args[1]));
                    }

                    return Optional.of(new GenericDiskItem(ItemType.GENERIC_DISK, new BigInteger("1000")));
                case FOUR_K_DISK:
                    return Optional.of(new FourKDiskItem());
                default:
                    return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Collection<String> getRegisteredItems() {
        return Arrays.stream(ItemType.values()).map(Enum::name).collect(Collectors.toList());
    }

    @Getter @Setter
    private static ConcurrentHashMap<Class<?>, RestoredItem> itemStore = new ConcurrentHashMap<>();

    public static <C extends RestoredItem> void registerItem(C item) {
        itemStore.put(item.getClass(), item);
    }

    public static <C extends RestoredItem> Optional<C> getDefaultItem(Class<C> clazz) {
        return Optional.ofNullable((C) itemStore.get(clazz));
    }

    public static void init() {
        Arrays.stream(ItemType.values()).forEach(type -> {
            RestoredItem item = getItem(type.name(), "1000").orElse(null);
            if (item != null) {
                registerItem(item);
            }
        });
    }

    public static boolean isDiskItem(ItemType type) {
        return type == ItemType.GENERIC_DISK || type == ItemType.FOUR_K_DISK;
    }

    public static boolean isDiskItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (isDiskItem(getTypeFrom(stack))) return true;
        return readDiskIdentifier(stack).isPresent();
    }
}
