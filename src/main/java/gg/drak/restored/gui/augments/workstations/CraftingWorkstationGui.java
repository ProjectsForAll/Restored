package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.gui.augments.AugmentRecipeService;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftingWorkstationGui extends AbstractWorkstationGui {
    private static final int[] GRID_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int PREVIEW_SLOT = 24;
    private static final int WORKSTATION_SLOT = 15;

    public CraftingWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.CRAFTING);
    }

    @Override
    protected int outputToggleInvSlot() {
        return 39; // under crafting grid
    }

    @Override
    protected int outputBufferInvSlot() {
        return 40;
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        for (int i = 0; i < 9; i++) {
            contents[GRID_SLOTS[i]] = displaySlot(i, "Craft Slot");
            bindCraftSlot(GRID_SLOTS[i], i);
        }
        ItemStack preview = AugmentRecipeService.matchCrafting(matrix());
        if (preview != null) {
            contents[PREVIEW_SLOT] = withLore(preview, List.of("#AAAAAAPreview (not taken)"));
        } else {
            contents[PREVIEW_SLOT] = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        }
        contents[WORKSTATION_SLOT] = workstationButton(List.of(
                "#bdc8c9Place items from inventory or pick from network.",
                "#bdc8c9Use the pane under the grid to choose output."
        ));
        bindWorkstation(WORKSTATION_SLOT);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        int times = clickType.isShiftClick() ? 64 : 1;
        int crafted = 0;
        for (int n = 0; n < times; n++) {
            ItemStack[] before = matrix();
            ItemStack result = AugmentRecipeService.matchCrafting(before);
            if (result == null) {
                break;
            }
            if (!canAcceptResult(result)) {
                break;
            }
            ItemStack[] templates = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                if (before[i] != null && !before[i].getType().isAir()) {
                    templates[i] = before[i].clone();
                    templates[i].setAmount(1);
                }
            }
            consumeOneCraft();
            if (!depositResult(result)) {
                break;
            }
            crafted++;
            for (int i = 0; i < 9; i++) {
                if (templates[i] == null) {
                    continue;
                }
                ItemStack current = craftSlots.get(i);
                if (current == null || current.getType().isAir()) {
                    refillFromNetwork(i, templates[i], 1);
                }
            }
        }
        if (crafted > 0) {
            player.sendMessage(LegacyColors.color("#00FC88Crafted x" + crafted + "."));
            network.save();
        } else {
            player.sendMessage(LegacyColors.color("#FF5555No valid recipe, missing items, or output blocked."));
        }
        render();
    }

    private ItemStack[] matrix() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = craftSlots.get(i);
        }
        return matrix;
    }

    private void consumeOneCraft() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftSlots.get(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                craftSlots.remove(i);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                craftSlots.put(i, stack);
            }
        }
    }
}
