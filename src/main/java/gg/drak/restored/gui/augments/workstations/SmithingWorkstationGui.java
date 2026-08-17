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

public class SmithingWorkstationGui extends AbstractWorkstationGui {
    private static final int TEMPLATE = 0;
    private static final int BASE = 1;
    private static final int ADDITION = 2;
    private static final int T_INV = 19;
    private static final int B_INV = 21;
    private static final int A_INV = 23;
    private static final int RESULT_INV = 25;
    private static final int WORK_INV = 13;

    public SmithingWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.SMITHING);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(TEMPLATE, BASE, ADDITION);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[T_INV] = displaySlot(TEMPLATE, "Smithing Template");
        bindCraftSlot(T_INV, TEMPLATE);
        contents[B_INV] = displaySlot(BASE, "Base");
        bindCraftSlot(B_INV, BASE);
        contents[A_INV] = displaySlot(ADDITION, "Addition");
        bindCraftSlot(A_INV, ADDITION);
        ItemStack preview = AugmentRecipeService.matchSmithing(
                craftSlots.get(TEMPLATE), craftSlots.get(BASE), craftSlots.get(ADDITION));
        contents[RESULT_INV] = preview != null
                ? withLore(preview, List.of("#AAAAAAPreview"))
                : GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        contents[WORK_INV] = workstationButton(List.of("#bdc8c9Server smithing recipes."));
        bindWorkstation(WORK_INV);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        int times = clickType.isShiftClick() ? 64 : 1;
        int done = 0;
        for (int i = 0; i < times; i++) {
            ItemStack result = AugmentRecipeService.matchSmithing(
                    craftSlots.get(TEMPLATE), craftSlots.get(BASE), craftSlots.get(ADDITION));
            if (result == null) {
                break;
            }
            if (!canAcceptResult(result)) {
                break;
            }
            ItemStack t = cloneTemplate(craftSlots.get(TEMPLATE));
            ItemStack b = cloneTemplate(craftSlots.get(BASE));
            ItemStack a = cloneTemplate(craftSlots.get(ADDITION));
            consume(TEMPLATE);
            consume(BASE);
            consume(ADDITION);
            if (!depositResult(result)) {
                break;
            }
            done++;
            if (t != null && craftSlots.get(TEMPLATE) == null) {
                refillFromNetwork(TEMPLATE, t, 1);
            }
            if (b != null && craftSlots.get(BASE) == null) {
                refillFromNetwork(BASE, b, 1);
            }
            if (a != null && craftSlots.get(ADDITION) == null) {
                refillFromNetwork(ADDITION, a, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Smithing x" + done + "." : "#FF5555No smithing recipe."));
        render();
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
