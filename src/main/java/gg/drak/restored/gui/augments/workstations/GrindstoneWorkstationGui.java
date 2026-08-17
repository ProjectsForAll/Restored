package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GrindstoneWorkstationGui extends AbstractWorkstationGui {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int LEFT_INV = 20;
    private static final int RIGHT_INV = 22;
    private static final int RESULT_INV = 24;
    private static final int WORK_INV = 15;

    public GrindstoneWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.GRINDSTONE);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(LEFT, RIGHT);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[LEFT_INV] = displaySlot(LEFT, "Input A");
        bindCraftSlot(LEFT_INV, LEFT);
        contents[RIGHT_INV] = displaySlot(RIGHT, "Input B");
        bindCraftSlot(RIGHT_INV, RIGHT);
        ItemStack preview = computeResult();
        contents[RESULT_INV] = preview != null
                ? withLore(preview, List.of("#AAAAAAPreview"))
                : GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        contents[WORK_INV] = workstationButton(List.of("#bdc8c9Disenchant or combine repair."));
        bindWorkstation(WORK_INV);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        int times = clickType.isShiftClick() ? 64 : 1;
        int done = 0;
        for (int i = 0; i < times; i++) {
            ItemStack result = computeResult();
            if (result == null) {
                break;
            }
            if (!canAcceptResult(result)) {
                break;
            }
            ItemStack left = craftSlots.get(LEFT);
            ItemStack right = craftSlots.get(RIGHT);
            ItemStack leftT = cloneTemplate(left);
            ItemStack rightT = cloneTemplate(right);
            consumeBoth();
            if (!depositResult(result)) {
                break;
            }
            done++;
            if (leftT != null && craftSlots.get(LEFT) == null) {
                refillFromNetwork(LEFT, leftT, 1);
            }
            if (rightT != null && craftSlots.get(RIGHT) == null) {
                refillFromNetwork(RIGHT, rightT, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Processed x" + done + "." : "#FF5555Nothing to process."));
        render();
    }

    private ItemStack computeResult() {
        ItemStack left = craftSlots.get(LEFT);
        ItemStack right = craftSlots.get(RIGHT);
        if (left != null && right != null && left.getType() == right.getType()
                && left.getItemMeta() instanceof Damageable && right.getItemMeta() instanceof Damageable) {
            ItemStack result = left.clone();
            Damageable meta = (Damageable) result.getItemMeta();
            Damageable a = (Damageable) left.getItemMeta();
            Damageable b = (Damageable) right.getItemMeta();
            int max = result.getType().getMaxDurability();
            int remain = (max - a.getDamage()) + (max - b.getDamage()) + max / 20;
            meta.setDamage(Math.max(0, max - remain));
            // strip non-curse enchants
            ItemMeta im = (ItemMeta) meta;
            for (Enchantment enchant : List.copyOf(im.getEnchants().keySet())) {
                if (!enchant.isCursed()) {
                    im.removeEnchant(enchant);
                }
            }
            result.setItemMeta(im);
            result.setAmount(1);
            return result;
        }
        ItemStack single = left != null ? left : right;
        if (single == null) {
            return null;
        }
        if (single.getType() == Material.ENCHANTED_BOOK) {
            return new ItemStack(Material.BOOK);
        }
        ItemStack result = single.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || meta.getEnchants().isEmpty()) {
            return null;
        }
        for (Enchantment enchant : List.copyOf(meta.getEnchants().keySet())) {
            if (!enchant.isCursed()) {
                meta.removeEnchant(enchant);
            }
        }
        result.setItemMeta(meta);
        result.setAmount(1);
        return result;
    }

    private void consumeBoth() {
        consume(LEFT);
        consume(RIGHT);
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
