package gg.drak.restored.data.screens.items;

import gg.drak.thebase.objects.Identifiable;
import host.plas.bou.gui.items.ItemData;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.serialization.PersistedItemCodec;
import org.bukkit.Material;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
public class StoredItem implements Identifiable {
    private String identifier; // in UUID format
    private String diskIdentifier; // UUID of the StorageDisk that owns this item
    private BigInteger amount;
    private ItemStack item;

    public UUID getUuid() {
        return UUID.fromString(identifier);
    }

    public StoredItem(String identifier, BigInteger amount, ItemStack item) {
        this(identifier, null, amount, item);
    }

    public StoredItem(String identifier, String diskIdentifier, BigInteger amount, ItemStack item) {
        this.identifier = identifier;
        this.diskIdentifier = diskIdentifier;
        this.amount = amount;
        this.item = flattenStack(item);
    }

    public StoredItem(ItemData data) {
        this(data, null);
    }

    public StoredItem(ItemData data, String diskIdentifier) {
        this.identifier = data.getIdentifier();
        this.diskIdentifier = diskIdentifier;
        this.amount = data.getAmount();
        this.item = flattenStack(PersistedItemCodec.deserializePayload(data.getData()));
    }

    public static ItemStack flattenStack(ItemStack stack) {
        if (stack == null) return null;
        ItemStack newStack = stack.clone();
        newStack.setAmount(1);
        return newStack;
    }

    public ItemData toData() {
        return new ItemData(identifier, amount, item);
    }

    public boolean isComparable(ItemStack stack) {
        if (stack == null) return false;
        ItemStack flattened = flattenStack(stack);

        return this.item.isSimilar(flattened);
    }

    public Icon asPageItem() {
        ItemStack stack = item.clone();
        stack.setAmount(1);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name;
            if (meta.hasDisplayName()) {
                name = meta.getDisplayName();
            } else {
                name = formatMaterialName(stack.getType());
            }
            meta.setDisplayName(ColorUtils.colorizeHard("&f" + name));
            meta.setLore(getPageLore(stack));

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(e -> {
            NetworkManager.onClickItem(this, e);
        });

        return icon;
    }

    private static String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    public List<String> getPageLore(ItemStack of) {
        ItemMeta meta = of.getItemMeta();

        List<String> lore = new ArrayList<>();
        if (meta != null) {
            lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
        }

        lore.add("");

        lore.add(ColorUtils.colorizeHard("&d&m     &r Item Info &d&m     &r"));
        lore.add(ColorUtils.colorizeHard("&7Amount: &f" + amount.toString()));
        lore.add(ColorUtils.colorizeHard("&7UUID: &f" + getUuid()));

        return lore;
    }
}
