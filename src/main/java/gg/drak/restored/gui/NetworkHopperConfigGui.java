package gg.drak.restored.gui;

import gg.drak.restored.data.Network;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.NetworkHopperStorage;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Configuration GUI for an output network hopper. */
public class NetworkHopperConfigGui extends AbstractInventoryGui {
    private static final int FILTER_ROW = 1;
    private static final int MAX_STACK_SLOT = 31;
    private final Block block;

    public NetworkHopperConfigGui(Player player, Block block) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(49).build());
        this.block = block;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        if (!NetworkHopperStorage.isHopper(block)
                || NetworkHopperStorage.role(block) != gg.drak.restored.data.NetworkHopperRole.OUTPUT) {
            player.closeInventory();
            return;
        }
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lOutput Hopper Configuration");
        List<ItemStack> filters = NetworkHopperStorage.getFilters(block);
        for (int col = 1; col <= NetworkHopperStorage.FILTER_SLOTS; col++) {
            int slot = FILTER_ROW * 9 + col;
            int index = col - 1;
            ItemStack filter = filters.get(index);
            contents[slot] = filter == null || filter.getType().isAir()
                    ? GuiItems.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "#AAAAAAOutput Filter " + (index + 1),
                    List.of("#bdc8c9Click to select an item from the network."))
                    : withHint(filter, List.of("#bdc8c9Click to remove or replace this filter."));
            bindSlot(slot, "filter:" + index);
        }
        int max = NetworkHopperStorage.getMaxStackSize(block);
        contents[MAX_STACK_SLOT] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lMax Stack Size: " + max,
                List.of(
                        "#bdc8c9Click to cycle 1, 16, 32, and 64.",
                        "#AAAAAAEach filter item is filled only up to this amount."
                )
        );
        bindSlot(MAX_STACK_SLOT, "max");
        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    private static ItemStack withHint(ItemStack stack, List<String> hints) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            hints.forEach(line -> lore.add(LegacyColors.color(line)));
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory().equals(player.getInventory())) {
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir() && placeIntoFilterSlots(clicked) > 0) {
                if (clicked.getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                render();
            }
            return;
        }
        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("back".equals(key)) {
            player.closeInventory();
            return;
        }
        if ("max".equals(key)) {
            int current = NetworkHopperStorage.getMaxStackSize(block);
            int next = current == 1 ? 16 : current == 16 ? 32 : current == 32 ? 64 : 1;
            NetworkHopperStorage.setMaxStackSize(block, next);
            render();
            return;
        }
        if (key.startsWith("filter:")) {
            handleFilterClick(Integer.parseInt(key.substring("filter:".length())), event);
        }
    }

    private void handleFilterClick(int index, InventoryClickEvent event) {
        List<ItemStack> filters = NetworkHopperStorage.getFilters(block);
        ItemStack existing = filters.get(index);
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            filters.set(index, cursor.clone());
            event.getView().setCursor(existing == null ? null : existing.clone());
            NetworkHopperStorage.setFilters(block, filters);
            render();
            return;
        }
        if (existing != null && !existing.getType().isAir()) {
            filters.set(index, null);
            NetworkHopperStorage.setFilters(block, filters);
            event.getView().setCursor(existing.clone());
            render();
            return;
        }
        openFilterPicker(index);
    }

    private int placeIntoFilterSlots(ItemStack fromPlayer) {
        List<ItemStack> filters = NetworkHopperStorage.getFilters(block);
        int moved = 0;
        for (int i = 0; i < filters.size() && fromPlayer.getAmount() > 0; i++) {
            if (filters.get(i) == null || filters.get(i).getType().isAir()) {
                filters.set(i, fromPlayer.clone());
                moved += fromPlayer.getAmount();
                fromPlayer.setAmount(0);
            }
        }
        if (moved > 0) {
            NetworkHopperStorage.setFilters(block, filters);
        }
        return moved;
    }

    private void openFilterPicker(int index) {
        Network network = NetworkHopperStorage.resolveNetwork(block);
        if (network == null) {
            player.sendMessage(LegacyColors.color("#FF5555Link this output hopper to a network first."));
            return;
        }
        new NetworkHopperFilterPickerGui(player, network, picked -> {
            if (picked != null) {
                List<ItemStack> filters = NetworkHopperStorage.getFilters(block);
                filters.set(index, picked);
                NetworkHopperStorage.setFilters(block, filters);
            }
            new NetworkHopperConfigGui(player, block).open();
        }).open();
    }
}
