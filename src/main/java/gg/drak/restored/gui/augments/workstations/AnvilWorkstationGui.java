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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnvilWorkstationGui extends AbstractWorkstationGui {
    private static final int BASE = 0;
    private static final int SACRIFICE = 1;
    private static final int BASE_INV = 20;
    private static final int SAC_INV = 22;
    private static final int RESULT_INV = 24;
    private static final int WORK_INV = 15;

    public AnvilWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.ANVIL);
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(BASE, SACRIFICE);
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[BASE_INV] = displaySlot(BASE, "Base Item");
        bindCraftSlot(BASE_INV, BASE);
        contents[SAC_INV] = displaySlot(SACRIFICE, "Sacrifice");
        bindCraftSlot(SAC_INV, SACRIFICE);
        AnvilResult preview = compute();
        if (preview != null) {
            contents[RESULT_INV] = withLore(preview.result(), List.of(
                    "#AAAAAAPreview",
                    "#bdc8c9XP cost: #FFED6A" + preview.levels()
            ));
        } else {
            contents[RESULT_INV] = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        }
        contents[WORK_INV] = workstationButton(List.of("#bdc8c9Uses your experience levels."));
        bindWorkstation(WORK_INV);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        int times = clickType.isShiftClick() ? 64 : 1;
        int done = 0;
        for (int i = 0; i < times; i++) {
            AnvilResult result = compute();
            if (result == null) {
                break;
            }
            if (player.getLevel() < result.levels()) {
                player.sendMessage(LegacyColors.color("#FF5555Need " + result.levels() + " levels."));
                break;
            }
            if (!canAcceptResult(result.result())) {
                break;
            }
            ItemStack baseT = cloneTemplate(craftSlots.get(BASE));
            ItemStack sacT = cloneTemplate(craftSlots.get(SACRIFICE));
            consume(BASE);
            consume(SACRIFICE);
            player.setLevel(player.getLevel() - result.levels());
            if (!depositResult(result.result())) {
                break;
            }
            done++;
            if (baseT != null && craftSlots.get(BASE) == null) {
                refillFromNetwork(BASE, baseT, 1);
            }
            if (sacT != null && craftSlots.get(SACRIFICE) == null) {
                refillFromNetwork(SACRIFICE, sacT, 1);
            }
        }
        player.sendMessage(LegacyColors.color(done > 0 ? "#00FC88Anvil used x" + done + "." : "#FF5555Cannot combine."));
        render();
    }

    private AnvilResult compute() {
        ItemStack base = craftSlots.get(BASE);
        ItemStack sac = craftSlots.get(SACRIFICE);
        if (base == null || sac == null) {
            return null;
        }
        ItemStack result = base.clone();
        result.setAmount(1);
        int cost = 1;
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return null;
        }

        if (base.getType() == sac.getType() && meta instanceof Damageable damageable
                && sac.getItemMeta() instanceof Damageable sacDamage) {
            int max = result.getType().getMaxDurability();
            if (max > 0) {
                int remain = (max - damageable.getDamage()) + (max - sacDamage.getDamage()) + max / 12;
                damageable.setDamage(Math.max(0, max - Math.min(max, remain)));
                meta = (ItemMeta) damageable;
                cost += 2;
            }
        }

        Map<Enchantment, Integer> toAdd = new HashMap<>();
        if (sac.getItemMeta() instanceof EnchantmentStorageMeta book) {
            toAdd.putAll(book.getStoredEnchants());
            cost += book.getStoredEnchants().size();
        } else if (sac.getItemMeta() != null) {
            toAdd.putAll(sac.getItemMeta().getEnchants());
            cost += sac.getItemMeta().getEnchants().size();
        }
        for (Map.Entry<Enchantment, Integer> entry : toAdd.entrySet()) {
            int current = meta.getEnchantLevel(entry.getKey());
            int next = Math.max(current, entry.getValue());
            if (current > 0 && current == entry.getValue()) {
                next = Math.min(entry.getKey().getMaxLevel(), current + 1);
            }
            if (entry.getKey().canEnchantItem(result) || result.getType() == Material.ENCHANTED_BOOK) {
                meta.addEnchant(entry.getKey(), next, true);
            }
        }
        result.setItemMeta(meta);
        return new AnvilResult(result, Math.max(1, cost));
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

    private record AnvilResult(ItemStack result, int levels) {
    }
}
