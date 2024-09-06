package host.plas.restored.data.items;

import host.plas.restored.data.items.impl.*;
import host.plas.restored.utils.MessageUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class ItemManager {
    public static String deserializeType(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (! container.has(RestoredItem.getTypeKey())) {
            MessageUtils.logInfo("Item has no type key...");
            return null;
        }

        return container.get(RestoredItem.getTypeKey(), PersistentDataType.STRING);
    }

    public static ItemType getTypeFrom(ItemStack stack) {
        String type = deserializeType(stack);
        if (type == null) {
            MessageUtils.logInfo("Item has no type key...");
            return ItemType.NONE;
        }

        try {
            return ItemType.valueOf(type);
        } catch (Exception e) {
            MessageUtils.logWarning("Error while deserializing item type: " + type, e);

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
                return new GenericDiskItem(readCapacity(stack));
            case FOUR_K_DISK:
                return new FourKDiskItem();
            default:
                MessageUtils.logInfo("Item has no type key...");
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
                        return Optional.of(new GenericDiskItem(new BigInteger(args[0])));
                    }

                    return Optional.of(new GenericDiskItem(new BigInteger("1000")));
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
}
