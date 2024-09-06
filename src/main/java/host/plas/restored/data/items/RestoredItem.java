package host.plas.restored.data.items;

import host.plas.bou.utils.ColorUtils;
import host.plas.restored.Restored;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public abstract class RestoredItem {
    private ItemType type;
    private Material material;
    private String displayName;
    private String[] lore;

    public RestoredItem(ItemType type, Material material, String displayName, String... lore) {
        this.type = type;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
    }

    public abstract void updateLore();

    public ItemStack getItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorizeHard(displayName));

            List<String> lore = new ArrayList<>();
            for (String s : this.lore) {
                lore.add(ColorUtils.colorizeHard(s));
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        item = applyEdits(item);

        stampItem(item);

        return item;
    }

    public ItemStack applyEdits(ItemStack stack) {
        return stack;
    }

    public boolean isSimilar(ItemStack item) {
        return getItem().isSimilar(item) && hasItemStamp(item);
    }

    public boolean hasItemStamp(ItemStack item) {
        return getItemStamp(item).equals(type.name());
    }

    public String getItemStamp(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(getTypeKey(), PersistentDataType.STRING)) {
                return container.get(getTypeKey(), PersistentDataType.STRING);
            }
        }

        return null;
    }

    public void stampItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(getTypeKey(), PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
    }

    public static NamespacedKey getTypeKey() {
        return new NamespacedKey(Restored.getInstance(), "item-type");
    }
}
