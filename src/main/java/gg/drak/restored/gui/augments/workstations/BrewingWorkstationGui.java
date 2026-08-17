package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

@SuppressWarnings("deprecation")
public class BrewingWorkstationGui extends AbstractWorkstationGui {
    private static final int BOTTLE_0 = 0;
    private static final int BOTTLE_1 = 1;
    private static final int BOTTLE_2 = 2;
    private static final int INGREDIENT = 3;
    private static final int FUEL = 4;
    private static final int BREW_TIME = 400;

    private static final int[] BOTTLE_INV = {29, 31, 33};
    private static final int ING_INV = 13;
    private static final int FUEL_INV = 11;
    private static final int PROGRESS_INV = 22;
    private static final int WORK_INV = 15;

    private int brewProgress;
    private int remaining;
    private boolean brewing;
    private BukkitTask task;
    private int blazeFuel;

    public BrewingWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.BREWING);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(BOTTLE_0, BOTTLE_1, BOTTLE_2, INGREDIENT, FUEL);
    }

    @Override
    protected int outputToggleInvSlot() {
        return 41;
    }

    @Override
    protected int outputBufferInvSlot() {
        return 42;
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        for (int i = 0; i < 3; i++) {
            contents[BOTTLE_INV[i]] = displaySlot(i, "Bottle Slot");
            bindCraftSlot(BOTTLE_INV[i], i);
        }
        contents[ING_INV] = displaySlot(INGREDIENT, "Ingredient");
        bindCraftSlot(ING_INV, INGREDIENT);
        contents[FUEL_INV] = displaySlot(FUEL, "Blaze Powder");
        bindCraftSlot(FUEL_INV, FUEL);

        int pct = brewing ? (int) ((brewProgress / (double) BREW_TIME) * 100) : 0;
        contents[PROGRESS_INV] = GuiItems.button(
                brewing ? Material.ORANGE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                brewing ? "#FFED6A&lBrewing..." : "#AAAAAAIdle",
                List.of(
                        "#bdc8c9Progress: #FFED6A" + pct + "%",
                        "#bdc8c9Blaze fuel: #FFED6A" + blazeFuel
                )
        );
        contents[WORK_INV] = workstationButton(List.of(
                "#bdc8c9Brew time: #FFED6A" + BREW_TIME + " ticks",
                "#bdc8c9Requires blaze powder fuel."
        ));
        bindWorkstation(WORK_INV);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        if (brewing) {
            player.sendMessage(LegacyColors.color("#FF5555Already brewing."));
            return;
        }
        if (!canBrew()) {
            player.sendMessage(LegacyColors.color("#FF5555Need bottles, ingredient, and blaze powder."));
            return;
        }
        remaining = clickType.isShiftClick() ? 64 : 1;
        brewProgress = 0;
        brewing = true;
        task = Bukkit.getScheduler().runTaskTimer(Restored.getInstance(), this::tickBrew, 1L, 1L);
        render();
    }

    private void tickBrew() {
        if (!brewing) {
            return;
        }
        if (!ensureBlazeFuel()) {
            stopBrew();
            player.sendMessage(LegacyColors.color("#FF5555Out of blaze powder."));
            render();
            return;
        }
        if (!canBrew()) {
            stopBrew();
            player.sendMessage(LegacyColors.color("#FF5555Missing bottles or ingredient."));
            render();
            return;
        }
        brewProgress++;
        if (brewProgress >= BREW_TIME) {
            applyBrew();
            remaining--;
            brewProgress = 0;
            if (remaining <= 0) {
                stopBrew();
                player.sendMessage(LegacyColors.color("#00FC88Brewing complete."));
            }
        }
        if (player.getOpenInventory().getTopInventory().getHolder() == this) {
            render();
        }
    }

    private boolean canBrew() {
        ItemStack ingredient = craftSlots.get(INGREDIENT);
        if (ingredient == null || transformType(ingredient.getType()) == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack bottle = craftSlots.get(i);
            if (bottle != null && isBrewableBottle(bottle)) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureBlazeFuel() {
        if (blazeFuel > 0) {
            return true;
        }
        ItemStack fuel = craftSlots.get(FUEL);
        if (fuel == null || fuel.getType() != Material.BLAZE_POWDER) {
            return false;
        }
        blazeFuel = 20; // vanilla: 20 brews per powder
        if (fuel.getAmount() <= 1) {
            craftSlots.remove(FUEL);
        } else {
            fuel.setAmount(fuel.getAmount() - 1);
            craftSlots.put(FUEL, fuel);
        }
        return true;
    }

    private void applyBrew() {
        ItemStack ingredient = craftSlots.get(INGREDIENT);
        if (ingredient == null) {
            return;
        }
        Material ing = ingredient.getType();
        PotionType target = transformType(ing);
        if (target == null) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack bottle = craftSlots.get(i);
            if (bottle == null || !isBrewableBottle(bottle)) {
                continue;
            }
            ItemStack result = bottle.clone();
            result.setAmount(1);
            if (ing == Material.GUNPOWDER && bottle.getType() == Material.POTION) {
                result.setType(Material.SPLASH_POTION);
            } else if (ing == Material.DRAGON_BREATH && bottle.getType() == Material.SPLASH_POTION) {
                result.setType(Material.LINGERING_POTION);
            } else if (result.getItemMeta() instanceof PotionMeta meta) {
                PotionData data = meta.getBasePotionData();
                boolean extended = ing == Material.REDSTONE || data.isExtended();
                boolean upgraded = ing == Material.GLOWSTONE_DUST || data.isUpgraded();
                if (ing == Material.REDSTONE) {
                    upgraded = false;
                }
                if (ing == Material.GLOWSTONE_DUST) {
                    extended = false;
                }
                PotionType type = target == PotionType.WATER ? data.getType() : target;
                if (ing != Material.REDSTONE && ing != Material.GLOWSTONE_DUST
                        && ing != Material.GUNPOWDER && ing != Material.DRAGON_BREATH
                        && ing != Material.FERMENTED_SPIDER_EYE) {
                    type = target;
                }
                if (ing == Material.FERMENTED_SPIDER_EYE) {
                    type = corrupt(data.getType());
                }
                meta.setBasePotionData(new PotionData(type, extended && !upgraded, upgraded && !extended));
                result.setItemMeta(meta);
            }
            if (!canAcceptResult(result)) {
                continue;
            }
            depositResult(result);
            if (bottle.getAmount() <= 1) {
                craftSlots.remove(i);
            } else {
                bottle.setAmount(bottle.getAmount() - 1);
                craftSlots.put(i, bottle);
            }
        }
        if (ingredient.getAmount() <= 1) {
            craftSlots.remove(INGREDIENT);
        } else {
            ingredient.setAmount(ingredient.getAmount() - 1);
            craftSlots.put(INGREDIENT, ingredient);
        }
        blazeFuel--;
        network.save();
    }

    private static boolean isBrewableBottle(ItemStack stack) {
        Material type = stack.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION || type == Material.GLASS_BOTTLE;
    }

    private static PotionType transformType(Material ingredient) {
        return switch (ingredient) {
            case NETHER_WART -> PotionType.AWKWARD;
            case SUGAR -> PotionType.SWIFTNESS;
            case RABBIT_FOOT -> PotionType.LEAPING;
            case BLAZE_POWDER -> PotionType.STRENGTH;
            case GLISTERING_MELON_SLICE -> PotionType.HEALING;
            case SPIDER_EYE -> PotionType.POISON;
            case GHAST_TEAR -> PotionType.REGENERATION;
            case MAGMA_CREAM -> PotionType.FIRE_RESISTANCE;
            case PUFFERFISH -> PotionType.WATER_BREATHING;
            case GOLDEN_CARROT -> PotionType.NIGHT_VISION;
            case TURTLE_HELMET -> PotionType.TURTLE_MASTER;
            case PHANTOM_MEMBRANE -> PotionType.SLOW_FALLING;
            case REDSTONE, GLOWSTONE_DUST, GUNPOWDER, DRAGON_BREATH, FERMENTED_SPIDER_EYE -> PotionType.WATER;
            default -> null;
        };
    }

    private static PotionType corrupt(PotionType type) {
        return switch (type) {
            case NIGHT_VISION -> PotionType.INVISIBILITY;
            case SWIFTNESS -> PotionType.SLOWNESS;
            case LEAPING -> PotionType.SLOWNESS;
            case HEALING -> PotionType.HARMING;
            case POISON -> PotionType.HARMING;
            case REGENERATION -> PotionType.WEAKNESS;
            default -> PotionType.WEAKNESS;
        };
    }

    private void stopBrew() {
        brewing = false;
        remaining = 0;
        brewProgress = 0;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    protected void returnSlotsToNetwork() {
        stopBrew();
        super.returnSlotsToNetwork();
    }

    @Override
    protected boolean allowCraftSlotEdit() {
        return !brewing;
    }

    @Override
    protected void openPicker(int slotId) {
        if (brewing) {
            player.sendMessage(LegacyColors.color("#FF5555Cannot change slots while brewing."));
            return;
        }
        super.openPicker(slotId);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        stopBrew();
        super.handleClose(event);
    }
}
