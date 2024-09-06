package host.plas.restored.data.items.impl;

import host.plas.restored.Restored;
import host.plas.restored.data.items.ItemType;
import host.plas.restored.data.items.RestoredItem;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigInteger;

@Getter @Setter
public class GenericDiskItem extends RestoredItem {
    private BigInteger size;

    public GenericDiskItem(BigInteger size) {
        super(ItemType.GENERIC_DISK,
                Material.PAPER,
                getSizedName(size),
                "&7Size&8: &a" + size + " &7Item Disk"
        );

        this.size = size;
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

        stack.setItemMeta(meta);

        return stack;
    }

    @Override
    public void updateLore() {

    }

    public static NamespacedKey getCapacityKey() {
        return new NamespacedKey(Restored.getInstance(), "capacity");
    }
}
