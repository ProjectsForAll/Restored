package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.gui.augments.AugmentRecipeService;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class FurnaceWorkstationGui extends AbstractWorkstationGui {
    private static final int INPUT_SLOT_ID = 0;
    private static final int FUEL_SLOT_ID = 1;
    private static final int INPUT_INV = 20;
    private static final int FUEL_INV = 29;
    private static final int PROGRESS_INV = 22;
    private static final int RESULT_INV = 24;
    private static final int WORKSTATION_INV = 15;

    private int burnTimeLeft;
    private int cookProgress;
    private int cookTimeNeeded;
    private int remainingCrafts;
    private ItemStack pendingResult;
    private BukkitTask cookTask;
    private boolean cooking;

    public FurnaceWorkstationGui(Player player, Network network, AugmentType type) {
        super(player, network, type);
        if (type.getLayout() != AugmentType.Layout.FURNACE) {
            throw new IllegalArgumentException("Not a furnace-family augment");
        }
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[INPUT_INV] = displaySlot(INPUT_SLOT_ID, "Input");
        bindCraftSlot(INPUT_INV, INPUT_SLOT_ID);
        contents[FUEL_INV] = displaySlot(FUEL_SLOT_ID, "Fuel");
        bindCraftSlot(FUEL_INV, FUEL_SLOT_ID);

        int pct = cookTimeNeeded <= 0 ? 0 : (int) ((cookProgress / (double) cookTimeNeeded) * 100);
        Material progressMat = cooking ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        contents[PROGRESS_INV] = GuiItems.button(
                progressMat,
                cooking ? "#FFED6A&lCooking..." : "#AAAAAAIdle",
                List.of(
                        "#bdc8c9Progress: #FFED6A" + pct + "%",
                        "#bdc8c9Burn time: #FFED6A" + burnTimeLeft,
                        cooking ? "#bdc8c9Remaining: #FFED6A" + remainingCrafts : "#AAAAAAClick workstation to start."
                )
        );

        if (pendingResult != null) {
            contents[RESULT_INV] = withLore(pendingResult, List.of("#AAAAAANext output"));
        } else {
            ItemStack input = craftSlots.get(INPUT_SLOT_ID);
            AugmentRecipeService.CookingMatch match = AugmentRecipeService.matchCooking(augmentType, input);
            if (match != null) {
                contents[RESULT_INV] = withLore(match.result(), List.of(
                        "#AAAAAAPreview",
                        "#bdc8c9Cook time: #FFED6A" + match.cookTimeTicks() + " ticks"
                ));
            } else {
                contents[RESULT_INV] = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
            }
        }

        contents[WORKSTATION_INV] = workstationButton(List.of(
                "#bdc8c9Uses server cook times.",
                "#bdc8c9Fuel is required in the fuel slot."
        ));
        bindWorkstation(WORKSTATION_INV);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(INPUT_SLOT_ID, FUEL_SLOT_ID);
    }

    @Override
    protected int maxPickAmount(int slotId) {
        return 64;
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            cancelCooking();
            returnSlotsToNetwork();
            render();
            player.sendMessage(LegacyColors.color("#00FC88Cleared crafting slots."));
            return;
        }
        if (cooking) {
            player.sendMessage(LegacyColors.color("#FF5555Already cooking."));
            return;
        }
        int times = clickType.isShiftClick() ? 64 : 1;
        startCooking(times);
    }

    private void startCooking(int times) {
        ItemStack input = craftSlots.get(INPUT_SLOT_ID);
        AugmentRecipeService.CookingMatch match = AugmentRecipeService.matchCooking(augmentType, input);
        if (match == null) {
            player.sendMessage(LegacyColors.color("#FF5555No valid cooking recipe."));
            return;
        }
        if (!ensureFuel()) {
            player.sendMessage(LegacyColors.color("#FF5555Need fuel in the fuel slot."));
            return;
        }
        remainingCrafts = times;
        cookTimeNeeded = match.cookTimeTicks();
        cookProgress = 0;
        pendingResult = match.result();
        cooking = true;
        cookTask = Bukkit.getScheduler().runTaskTimer(Restored.getInstance(), this::tickCook, 1L, 1L);
        render();
    }

    private void tickCook() {
        if (!cooking) {
            return;
        }
        if (!ensureFuel()) {
            cancelCooking();
            player.sendMessage(LegacyColors.color("#FF5555Out of fuel."));
            render();
            return;
        }
        ItemStack input = craftSlots.get(INPUT_SLOT_ID);
        AugmentRecipeService.CookingMatch match = AugmentRecipeService.matchCooking(augmentType, input);
        if (match == null) {
            cancelCooking();
            player.sendMessage(LegacyColors.color("#FF5555Input exhausted."));
            render();
            return;
        }
        cookTimeNeeded = match.cookTimeTicks();
        pendingResult = match.result();
        burnTimeLeft--;
        cookProgress++;
        if (cookProgress >= cookTimeNeeded) {
            ItemStack cooked = match.result();
            if (!canAcceptResult(cooked)) {
                cancelCooking();
                render();
                return;
            }
            ItemStack template = input.clone();
            template.setAmount(1);
            consumeInputOne();
            depositResult(cooked);
            remainingCrafts--;
            cookProgress = 0;
            refillFromNetwork(INPUT_SLOT_ID, template, craftSlots.get(INPUT_SLOT_ID) == null ? 1 : 0);
            if (craftSlots.get(INPUT_SLOT_ID) == null) {
                refillFromNetwork(INPUT_SLOT_ID, template, 1);
            }
            if (remainingCrafts <= 0) {
                cancelCooking();
                player.sendMessage(LegacyColors.color("#00FC88Cooking complete."));
            }
        }
        if (player.getOpenInventory().getTopInventory().getHolder() == this) {
            render();
        }
    }

    private boolean ensureFuel() {
        if (burnTimeLeft > 0) {
            return true;
        }
        ItemStack fuel = craftSlots.get(FUEL_SLOT_ID);
        int burn = AugmentRecipeService.fuelBurnTicks(fuel);
        if (burn <= 0 || fuel == null) {
            return false;
        }
        burnTimeLeft = burn;
        if (fuel.getAmount() <= 1) {
            // lava bucket becomes empty bucket
            if (fuel.getType() == Material.LAVA_BUCKET) {
                craftSlots.put(FUEL_SLOT_ID, new ItemStack(Material.BUCKET));
            } else {
                craftSlots.remove(FUEL_SLOT_ID);
            }
        } else {
            fuel.setAmount(fuel.getAmount() - 1);
            craftSlots.put(FUEL_SLOT_ID, fuel);
        }
        return true;
    }

    private void consumeInputOne() {
        ItemStack input = craftSlots.get(INPUT_SLOT_ID);
        if (input == null) {
            return;
        }
        if (input.getAmount() <= 1) {
            craftSlots.remove(INPUT_SLOT_ID);
        } else {
            input.setAmount(input.getAmount() - 1);
            craftSlots.put(INPUT_SLOT_ID, input);
        }
    }

    private void cancelCooking() {
        cooking = false;
        remainingCrafts = 0;
        cookProgress = 0;
        pendingResult = null;
        if (cookTask != null) {
            cookTask.cancel();
            cookTask = null;
        }
    }

    @Override
    protected void returnSlotsToNetwork() {
        cancelCooking();
        super.returnSlotsToNetwork();
    }

    @Override
    protected boolean allowCraftSlotEdit() {
        return !cooking;
    }

    @Override
    protected void openPicker(int slotId) {
        if (cooking) {
            player.sendMessage(LegacyColors.color("#FF5555Cannot change slots while cooking."));
            return;
        }
        super.openPicker(slotId);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        cancelCooking();
        super.handleClose(event);
    }
}
