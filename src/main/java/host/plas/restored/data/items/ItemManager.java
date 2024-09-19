package host.plas.restored.data.items;

import host.plas.restored.Restored;
import host.plas.restored.data.items.impl.*;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ItemManager {
    public static String deserializeType(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (! container.has(RestoredItem.getTypeKey())) {
            Restored.getInstance().logInfo("Item has no type key...");
            return null;
        }

        return container.get(RestoredItem.getTypeKey(), PersistentDataType.STRING);
    }

    public static ItemType getTypeFrom(ItemStack stack) {
        String type = deserializeType(stack);
        if (type == null) {
            Restored.getInstance().logInfo("Item has no type key...");
            return ItemType.NONE;
        }

        try {
            return ItemType.valueOf(type);
        } catch (Exception e) {
            Restored.getInstance().logWarning("Error while deserializing item type: " + type, e);

            return ItemType.NONE;
        }
    }

    public static RestoredItem readItem(ItemStack stack) {
        ItemType type = getTypeFrom(stack);
        switch (type) {
            case CONTROLLER:
                return new ControllerItem();
            case DRIVE:
                return new DriveItem();
            case VIEWER:
                return new ViewerItem();
            case GENERIC_DISK:
                return new GenericDiskItem(ItemType.GENERIC_DISK, readCapacity(stack));
            case FOUR_K_DISK:
                return new FourKDiskItem();
            default:
                Restored.getInstance().logInfo("Item has no type key...");
                return null;
        }
    }

    public static BigInteger readCapacity(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (! container.has(GenericDiskItem.getCapacityKey())) return null;

        return new BigInteger(container.get(GenericDiskItem.getCapacityKey(), PersistentDataType.STRING));
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
                case GENERIC_DISK:
                    if (args.length == 1) {
                        return Optional.of(new GenericDiskItem(ItemType.GENERIC_DISK, new BigInteger(args[0])));
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
        return isDiskItem(getTypeFrom(stack));
    }
}
