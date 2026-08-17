package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.items.NetworkUpgradeItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.LinkedChestStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Install / uninstall Network Upgrades for a network.
 * Row 2 center (slot 13): upgrade stack (amount = installed, capped at 64).
 * Row 3 center (slot 22): capacity status pane.
 * Bottom row center (slot 31): back button.
 */
public class NetworkUpgradesGui extends AbstractInventoryGui {
    private static final int UPGRADE_SLOT = 13; // row 2, slot 5 (1-indexed)
    private static final int STATUS_SLOT = 22; // row 3, slot 5 (1-indexed)
    private static final int BACK_SLOT = 31; // bottom row, slot 5 (1-indexed)
    private static final int MAX_DISPLAY = 64;

    private final Network network;

    public NetworkUpgradesGui(Player player, Network network) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
    }

    @Override
    public void open() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, "#FFED6A&lNetwork Upgrades");
        contents[UPGRADE_SLOT] = buildUpgradeSlot();
        contents[STATUS_SLOT] = buildStatusPane();
        bindSlot(UPGRADE_SLOT, "upgrades");
        bindSlot(STATUS_SLOT, "status");
        placeReturnButton(contents, "back");
        bindSlot(BACK_SLOT, "back");
        finishAndOpen(contents);
    }

    private ItemStack buildUpgradeSlot() {
        int installed = network.getUpgradeCount();
        if (installed <= 0) {
            return GuiItems.button(
                    Material.GOLD_INGOT,
                    "#FFED6A&lNetwork Upgrades",
                    List.of(
                            "&7Installed: &a0",
                            "",
                            "#bdc8c9Shift-click Network Upgrades",
                            "#bdc8c9from your inventory to install.",
                            "#AAAAAAEach upgrade adds 64 virtual capacity."
                    )
            );
        }

        ItemStack stack = NetworkUpgradeItem.create();
        int shown = Math.min(MAX_DISPLAY, installed);
        stack.setAmount(shown);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(LegacyColors.color("&7Installed: &a" + installed));
            lore.add("");
            lore.add(LegacyColors.color("#bdc8c9Shift-click this slot to uninstall"));
            lore.add(LegacyColors.color("#bdc8c9up to #FFED6A" + shown + " #bdc8c9upgrades."));
            lore.add(LegacyColors.color("#AAAAAAEach upgrade adds 64 virtual capacity."));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildStatusPane() {
        int installed = network.getUpgradeCount();
        boolean hasLinks = network.getLinkedChestCount() > 0;
        boolean hasStorage = installed > 0 || hasLinks;

        if (!hasStorage) {
            return GuiItems.button(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7Status: &fNOT INSTALLED",
                    List.of(
                            "&7Installed: &a0",
                            "",
                            "&cInstall Network Upgrades or link chests",
                            "&cto this network to add space!"
                    )
            );
        }

        boolean hasSpace = network.getTotalItems() < network.getCapacity()
                || LinkedChestStorage.hasAnyFreeSlot(network);
        if (hasSpace) {
            return GuiItems.button(
                    Material.LIME_STAINED_GLASS_PANE,
                    "&7Status: &aONLINE",
                    List.of("&7Installed: &a" + installed)
            );
        }
        return GuiItems.button(
                Material.RED_STAINED_GLASS_PANE,
                "&7Status: &cFULL",
                List.of("&7Installed: &a" + installed)
            );
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && NetworkUpgradeItem.isType(clicked)) {
                    if (installFromStack(clicked)) {
                        if (clicked.getAmount() <= 0) {
                            event.setCurrentItem(null);
                        }
                        open();
                    }
                }
            }
            return;
        }

        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if ("back".equals(key)) {
            player.closeInventory();
            new NetworkItemsGui(player, network).open();
            return;
        }
        if ("upgrades".equals(key) && event.isShiftClick()) {
            if (uninstallDisplayed()) {
                open();
            }
        }
    }

    private boolean installFromStack(ItemStack stack) {
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot install upgrades on this network."));
            return false;
        }
        int amount = stack.getAmount();
        if (amount <= 0) {
            return false;
        }
        for (int i = 0; i < amount; i++) {
            network.addUpgrade();
        }
        stack.setAmount(0);
        network.save();
        player.sendMessage(LegacyColors.color("#00FC88Installed #FFED6A" + amount
                + " #00FC88upgrade(s). Capacity is now #FFED6A" + network.getCapacity() + "#00FC88."));
        return true;
    }

    private boolean uninstallDisplayed() {
        if (!network.canManage(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot remove upgrades from this network."));
            return false;
        }
        int want = Math.min(MAX_DISPLAY, network.getUpgradeCount());
        if (want <= 0) {
            return false;
        }
        int removed = 0;
        for (int i = 0; i < want; i++) {
            if (!network.removeUpgrade()) {
                break;
            }
            removed++;
        }
        if (removed <= 0) {
            player.sendMessage(LegacyColors.color("#FF5555Cannot remove upgrades while virtual items exceed the lower capacity."));
            return false;
        }
        network.save();

        ItemStack give = NetworkUpgradeItem.create();
        give.setAmount(removed);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));

        if (removed < want) {
            player.sendMessage(LegacyColors.color("#FFED6ARemoved #AAAAAA" + removed
                    + " #FFED6Aupgrade(s); more would not fit virtual storage."));
        } else {
            player.sendMessage(LegacyColors.color("#00FC88Removed #FFED6A" + removed
                    + " #00FC88upgrade(s). Capacity is now #FFED6A" + network.getCapacity() + "#00FC88."));
        }
        return true;
    }
}
