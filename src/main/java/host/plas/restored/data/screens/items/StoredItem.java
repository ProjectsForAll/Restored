package host.plas.restored.data.screens.items;

import host.plas.restored.data.NetworkManager;
import host.plas.restored.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tv.quaint.objects.Identifiable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
public class StoredItem implements Identifiable {
    private String identifier; // in UUID format
    private UUID networkUuid; // in UUID format
    private BigInteger amount;
    private ItemStack item;

    public UUID getUuid() {
        return UUID.fromString(identifier);
    }

    public UUID getNetworkUuid() {
        return UUID.fromString(identifier);
    }

    public StoredItem(String identifier, BigInteger amount, ItemStack item) {
        this.identifier = identifier;
        this.amount = amount;
        this.item = flattenStack(item);
    }

    public static ItemStack flattenStack(ItemStack stack) {
        ItemStack newStack = stack.clone();
        newStack.setAmount(1);
        return newStack;
    }

    public ItemData toData() {
        return new ItemData(identifier, amount, item);
    }

    public boolean isComparable(ItemStack stack) {
        ItemStack flattened = flattenStack(stack);

        return this.item.equals(flattened);
    }

    public Icon asPageItem() {
        ItemStack stack = item.clone();
        stack.setAmount(1);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + meta.getDisplayName());
            meta.setLore(getPageLore(stack));

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(e -> {
            NetworkManager.onClickItem(this, e);
        });

        return icon;
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

        lore.add(MessageUtils.colorize("&d&m     &r Item Info &d&m     &r"));
        lore.add(MessageUtils.colorize("&7Amount: &f" + amount.toString()));
        lore.add(MessageUtils.colorize("&7UUID: &f" + getUuid()));

        return lore;
    }
}
