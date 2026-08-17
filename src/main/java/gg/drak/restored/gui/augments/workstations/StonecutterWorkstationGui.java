package gg.drak.restored.gui.augments.workstations;

import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.gui.augments.AugmentRecipeService;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StonecutterWorkstationGui extends AbstractWorkstationGui {
    private static final int INPUT = 0;
    private static final int INPUT_INV = 10;
    private static final int WORK_INV = 16;
    private final Map<Integer, ItemStack> resultChoices = new HashMap<>();
    private ItemStack selectedResult;

    public StonecutterWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.STONECUTTER);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(INPUT);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        resultChoices.clear();
        contents[INPUT_INV] = displaySlot(INPUT, "Input");
        bindCraftSlot(INPUT_INV, INPUT);
        contents[WORK_INV] = workstationButton(List.of(
                "#bdc8c9Click a result below to select it,",
                "#bdc8c9then click the stonecutter to cut."
        ));
        bindWorkstation(WORK_INV);

        List<ItemStack> results = AugmentRecipeService.matchStonecutter(craftSlots.get(INPUT));
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        // Use lower content rows (skip first content row around input)
        int placed = 0;
        for (int slot : slots) {
            if (slot < 19 || slot == outputToggleInvSlot() || slot == outputBufferInvSlot()) {
                continue;
            }
            if (placed >= results.size()) {
                break;
            }
            ItemStack result = results.get(placed);
            boolean selected = selectedResult != null && selectedResult.isSimilar(result);
            contents[slot] = withLore(result, List.of(
                    selected ? "#00FC88Selected" : "#bdc8c9Click to select"
            ));
            resultChoices.put(slot, result);
            bindSlot(slot, "result:" + placed);
            placed++;
        }
        if (selectedResult == null && !results.isEmpty()) {
            // keep selection if still valid
        } else if (selectedResult != null) {
            boolean stillValid = results.stream().anyMatch(r -> r.isSimilar(selectedResult));
            if (!stillValid) {
                selectedResult = null;
            }
        }
    }

    @Override
    protected void handleExtraClick(String key, InventoryClickEvent event) {
        if (key.startsWith("result:")) {
            ItemStack fromSlot = resultChoices.get(event.getRawSlot());
            if (fromSlot != null) {
                selectedResult = fromSlot.clone();
                render();
            }
        }
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        if (selectedResult == null) {
            player.sendMessage(LegacyColors.color("#FF5555Select a result first."));
            return;
        }
        int times = clickType.isShiftClick() ? 64 : 1;
        int done = 0;
        for (int i = 0; i < times; i++) {
            ItemStack input = craftSlots.get(INPUT);
            if (input == null) {
                break;
            }
            List<ItemStack> results = AugmentRecipeService.matchStonecutter(input);
            ItemStack match = null;
            for (ItemStack r : results) {
                if (r.isSimilar(selectedResult)) {
                    match = r.clone();
                    break;
                }
            }
            if (match == null) {
                break;
            }
            if (!canAcceptResult(match)) {
                break;
            }
            ItemStack template = input.clone();
            template.setAmount(1);
            if (input.getAmount() <= 1) {
                craftSlots.remove(INPUT);
            } else {
                input.setAmount(input.getAmount() - 1);
                craftSlots.put(INPUT, input);
            }
            if (!depositResult(match)) {
                break;
            }
            done++;
            if (craftSlots.get(INPUT) == null) {
                refillFromNetwork(INPUT, template, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Cut x" + done + "." : "#FF5555Cannot cut."));
        render();
    }

    @Override
    protected void openPicker(int slotId) {
        selectedResult = null;
        super.openPicker(slotId);
    }
}
