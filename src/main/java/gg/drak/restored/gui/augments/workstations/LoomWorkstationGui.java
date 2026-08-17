package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class LoomWorkstationGui extends AbstractWorkstationGui {
    private static final int BANNER = 0;
    private static final int DYE = 1;
    private static final int PATTERN = 2;
    private static final int B_INV = 20;
    private static final int D_INV = 22;
    private static final int P_INV = 24;
    private static final int RESULT_INV = 31;
    private static final int WORK_INV = 13;

    public LoomWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.LOOM);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(BANNER, DYE, PATTERN);
    }

    @Override
    protected int outputToggleInvSlot() {
        return 39;
    }

    @Override
    protected int outputBufferInvSlot() {
        return 40;
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[B_INV] = displaySlot(BANNER, "Banner");
        bindCraftSlot(B_INV, BANNER);
        contents[D_INV] = displaySlot(DYE, "Dye");
        bindCraftSlot(D_INV, DYE);
        contents[P_INV] = displaySlot(PATTERN, "Banner Pattern");
        bindCraftSlot(P_INV, PATTERN);
        ItemStack preview = compute();
        contents[RESULT_INV] = preview != null
                ? withLore(preview, List.of("#AAAAAAPreview"))
                : GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        contents[WORK_INV] = workstationButton(List.of("#bdc8c9Applies a pattern with dye."));
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
            ItemStack b = cloneTemplate(craftSlots.get(BANNER));
            ItemStack d = cloneTemplate(craftSlots.get(DYE));
            ItemStack p = cloneTemplate(craftSlots.get(PATTERN));
            consume(BANNER);
            consume(DYE);
            // pattern item is not always consumed in vanilla for some patterns; consume if present
            if (p != null) {
                consume(PATTERN);
            }
            if (!depositResult(result)) {
                break;
            }
            done++;
            if (b != null && craftSlots.get(BANNER) == null) {
                refillFromNetwork(BANNER, b, 1);
            }
            if (d != null && craftSlots.get(DYE) == null) {
                refillFromNetwork(DYE, d, 1);
            }
            if (p != null && craftSlots.get(PATTERN) == null) {
                refillFromNetwork(PATTERN, p, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Wove x" + done + "." : "#FF5555Need banner + dye."));
        render();
    }

    private ItemStack compute() {
        ItemStack banner = craftSlots.get(BANNER);
        ItemStack dye = craftSlots.get(DYE);
        if (banner == null || dye == null) {
            return null;
        }
        if (!(banner.getItemMeta() instanceof BannerMeta) && !banner.getType().name().endsWith("_BANNER")) {
            return null;
        }
        DyeColor color = dyeColor(dye.getType());
        if (color == null) {
            return null;
        }
        ItemStack result = banner.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        if (!(meta instanceof BannerMeta bannerMeta)) {
            return null;
        }
        PatternType patternType = resolvePattern(craftSlots.get(PATTERN));
        bannerMeta.addPattern(new Pattern(color, patternType));
        result.setItemMeta(bannerMeta);
        return result;
    }

    private static PatternType resolvePattern(ItemStack patternItem) {
        if (patternItem == null || patternItem.getType().isAir()) {
            return PatternType.BASE;
        }
        String name = patternItem.getType().name();
        if (name.contains("CREEPER")) {
            return PatternType.CREEPER;
        }
        if (name.contains("SKULL")) {
            return PatternType.SKULL;
        }
        if (name.contains("FLOWER")) {
            return PatternType.FLOWER;
        }
        if (name.contains("MOJANG")) {
            return PatternType.MOJANG;
        }
        if (name.contains("GLOBE")) {
            return PatternType.GLOBE;
        }
        if (name.contains("PIGLIN")) {
            return PatternType.PIGLIN;
        }
        return PatternType.BASE;
    }

    private static DyeColor dyeColor(Material material) {
        String name = material.name();
        if (!name.endsWith("_DYE")) {
            return null;
        }
        try {
            return DyeColor.valueOf(name.substring(0, name.length() - 4));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
