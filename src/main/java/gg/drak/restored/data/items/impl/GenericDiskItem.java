package gg.drak.restored.data.items.impl;

import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;
import java.util.UUID;

@Getter @Setter
public class GenericDiskItem extends RestoredItem {
    private BigInteger size;
    private String identifier;

    public GenericDiskItem(ItemType type, BigInteger size, String identifier) {
        super(type,
                Material.PAPER,
                getSizedName(size),
                "&7Size&8: &a" + size + " &7Item Disk"
        );

        this.size = size;
        this.identifier = identifier;
    }

    public GenericDiskItem(ItemType type, BigInteger size) {
        this(type, size, UUID.randomUUID().toString());
    }

    public static String getSizedName(BigInteger size) {
        return "&a" + size + " &7Item Disk";
    }

    @Override
    public ItemStack applyEdits(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(getCapacityKey(), PersistentDataType.STRING, size.toString());
        container.set(getIdentifierKey(), PersistentDataType.STRING, identifier);

        stack.setItemMeta(meta);

        return stack;
    }

    @Override
    public void updateLore() {

    }

    public static NamespacedKey getCapacityKey() {
        return ItemManager.getNamedKey(CAPACITY_KEY);
    }
    public static NamespacedKey getIdentifierKey() {
        return ItemManager.getNamedKey(IDENTIFIER_KEY);
    }

    public static String CAPACITY_KEY = "capacity";
    public static String IDENTIFIER_KEY = "disk-identifier";
}
