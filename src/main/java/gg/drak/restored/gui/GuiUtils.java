package gg.drak.restored.gui;

import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.items.NetworkUpgradeItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiUtils {

    private GuiUtils() {
    }

    public static ItemStack asGuiStack(StoredStack stored) {
        return asGuiStack(stored.getTemplate(), Math.min(64, stored.getAmount()), stored.getAmount());
    }

    public static ItemStack asGuiStack(NetworkItemsGui.DisplayEntry entry) {
        return asGuiStack(entry.template(), entry.stackAmount(), entry.totalAmount());
    }

    public static ItemStack asGuiStack(ItemStack template, long stackAmount, long totalAmount) {
        ItemStack display = template.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            } else {
                lore = new ArrayList<>(lore);
            }
            lore.add("");
            lore.add(LegacyColors.color("#AAAAAAStored: #FFED6A" + totalAmount));
            // Only when splitting large stores across multiple GUI slots.
            if (stackAmount > 1 && stackAmount < totalAmount) {
                lore.add(LegacyColors.color("#bdc8c9Showing stack of #FFED6A" + stackAmount));
            }
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        display.setAmount((int) Math.max(1, Math.min(64, stackAmount)));
        return display;
    }

    public static ItemStack networkIcon(Network network) {
        List<String> lore = new ArrayList<>();
        lore.add("#bdc8c9UUID: #AAAAAA" + network.getIdentifierString());
        if (network.isPlaced()) {
            lore.add("#bdc8c9Location: #AAAAAA" + network.getWorld() + " " + network.getX() + ", " + network.getY() + ", " + network.getZ());
        } else {
            lore.add("#bdc8c9Location: #FF5555Not placed");
        }
        lore.add("#bdc8c9Virtual items: #AAAAAA" + network.getTotalItems() + " / " + network.getCapacity());
        int linkedBlocks = LinkedChestStorage.countLinkedChestBlocks(network);
        if (linkedBlocks > 0) {
            lore.add("#bdc8c9Linked chests: #AAAAAA" + linkedBlocks);
        }
        lore.add("");
        lore.add("#bdc8c9Click to manage.");
        lore.add("#bdc8c9Left-click chest with upgrade to apply.");
        return GuiItems.button(
                org.bukkit.Material.CHEST,
                "#FFED6A&lNetwork",
                lore
        );
    }

    public static boolean tryApplyUpgrade(Player player, ItemStack item, Network network) {
        if (!NetworkUpgradeItem.isType(item)) {
            return false;
        }
        network.addUpgrade();
        network.save();
        item.setAmount(item.getAmount() - 1);
        player.sendMessage(LegacyColors.color("#00FC88Network upgraded! Capacity is now " + network.getCapacity() + "."));
        return true;
    }

    public static boolean isContentSlot(int slot, int inventorySize) {
        for (int contentSlot : GuiLayout.listContentSlots(inventorySize)) {
            if (contentSlot == slot) {
                return true;
            }
        }
        return false;
    }

    public static Map<Integer, String> mapContentKeys(List<StoredStack> pageItems) {
        Map<Integer, String> keys = new HashMap<>();
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        for (int i = 0; i < pageItems.size() && i < slots.length; i++) {
            keys.put(slots[i], pageItems.get(i).getTemplate() != null ? StoredStack.itemKey(pageItems.get(i).getTemplate()) : null);
        }
        return keys;
    }
}
