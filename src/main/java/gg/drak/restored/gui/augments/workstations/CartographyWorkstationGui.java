package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import java.util.List;

public class CartographyWorkstationGui extends AbstractWorkstationGui {
    private static final int MAP = 0;
    private static final int EXTRA = 1;
    private static final int MAP_INV = 20;
    private static final int EXTRA_INV = 22;
    private static final int RESULT_INV = 24;
    private static final int WORK_INV = 15;

    public CartographyWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.CARTOGRAPHY);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(MAP, EXTRA);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[MAP_INV] = displaySlot(MAP, "Map");
        bindCraftSlot(MAP_INV, MAP);
        contents[EXTRA_INV] = displaySlot(EXTRA, "Paper / Glass Pane / Map");
        bindCraftSlot(EXTRA_INV, EXTRA);
        ItemStack preview = compute();
        contents[RESULT_INV] = preview != null
                ? withLore(preview, List.of("#AAAAAAPreview"))
                : GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        contents[WORK_INV] = workstationButton(List.of("#bdc8c9Clone, lock, or zoom maps."));
        bindWorkstation(WORK_INV);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        int times = clickType.isShiftClick() ? 64 : 1;
        int done = 0;
        for (int i = 0; i < times; i++) {
            ItemStack result = compute();
            if (result == null) {
                break;
            }
            if (!canAcceptResult(result)) {
                break;
            }
            ItemStack mapT = cloneTemplate(craftSlots.get(MAP));
            ItemStack extraT = cloneTemplate(craftSlots.get(EXTRA));
            boolean consumeMap = craftSlots.get(EXTRA) != null
                    && craftSlots.get(EXTRA).getType() != Material.PAPER;
            // paper = clone (keep map), glass = lock (consume map), map+map = clone
            Material extraType = craftSlots.get(EXTRA) == null ? Material.AIR : craftSlots.get(EXTRA).getType();
            if (extraType == Material.GLASS_PANE) {
                consume(MAP);
                consume(EXTRA);
            } else if (extraType == Material.PAPER || extraType == Material.FILLED_MAP || extraType == Material.MAP) {
                // clone: consume paper/extra map only; keep original map in slot for refill pattern
                if (extraType == Material.PAPER) {
                    consume(EXTRA);
                } else {
                    consume(EXTRA);
                }
            } else {
                break;
            }
            if (!depositResult(result)) {
                break;
            }
            done++;
            if (consumeMap && mapT != null && craftSlots.get(MAP) == null) {
                refillFromNetwork(MAP, mapT, 1);
            }
            if (extraT != null && craftSlots.get(EXTRA) == null) {
                refillFromNetwork(EXTRA, extraT, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Cartography x" + done + "." : "#FF5555Invalid map combination."));
        render();
    }

    private ItemStack compute() {
        ItemStack map = craftSlots.get(MAP);
        ItemStack extra = craftSlots.get(EXTRA);
        if (map == null || extra == null) {
            return null;
        }
        if (map.getType() != Material.FILLED_MAP && map.getType() != Material.MAP) {
            return null;
        }
        if (extra.getType() == Material.PAPER) {
            ItemStack result = map.clone();
            result.setAmount(1);
            return result;
        }
        if (extra.getType() == Material.GLASS_PANE) {
            // Locking APIs vary by platform; return a distinct locked-style copy via lore.
            ItemStack result = map.clone();
            result.setAmount(1);
            if (result.getItemMeta() instanceof MapMeta meta) {
                java.util.List<String> lore = meta.getLore() == null
                        ? new java.util.ArrayList<>()
                        : new java.util.ArrayList<>(meta.getLore());
                lore.add(LegacyColors.color("#AAAAAALocked Map"));
                meta.setLore(lore);
                result.setItemMeta(meta);
            }
            return result;
        }
        if (extra.getType() == Material.FILLED_MAP || extra.getType() == Material.MAP) {
            ItemStack result = map.clone();
            result.setAmount(1);
            return result;
        }
        return null;
    }

    private void consume(int slot) {
        ItemStack stack = craftSlots.get(slot);
        if (stack == null) {
            return;
        }
        if (stack.getAmount() <= 1) {
            craftSlots.remove(slot);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            craftSlots.put(slot, stack);
        }
    }

    private static ItemStack cloneTemplate(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

}
