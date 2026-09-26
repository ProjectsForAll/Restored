package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.gui.augments.AugmentRecipeService;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CraftingWorkstationGui extends AbstractWorkstationGui {
    private static final int[] GRID_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int PREVIEW_SLOT = 24;
    private static final int WORKSTATION_SLOT = 15;

    private final AtomicBoolean craftingBusy = new AtomicBoolean(false);

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
    protected boolean allowCraftSlotEdit() {
        return !craftingBusy.get();
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        for (int i = 0; i < 9; i++) {
            contents[GRID_SLOTS[i]] = displaySlot(i, "Craft Slot");
            bindCraftSlot(GRID_SLOTS[i], i);
        }
        ItemStack preview = AugmentRecipeService.matchCrafting(matrix(), player.getWorld());
        if (preview != null) {
            contents[PREVIEW_SLOT] = withLore(preview, List.of("#AAAAAAPreview (not taken)"));
        } else {
            contents[PREVIEW_SLOT] = GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE);
        }
        List<String> lore = craftingBusy.get()
                ? List.of("#FFED6ACrafting in progress…")
                : List.of(
                "#bdc8c9Place items from inventory or pick from network.",
                "#bdc8c9Use the pane under the grid to choose output.",
                "#AAAAAAShift-click to craft up to a stack."
        );
        contents[WORKSTATION_SLOT] = workstationButton(lore);
        bindWorkstation(WORKSTATION_SLOT);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        if (!craftingBusy.compareAndSet(false, true)) {
            player.sendMessage(LegacyColors.color("#FF5555Already crafting — please wait."));
            return;
        }

        int maxRequested = clickType.isShiftClick() ? 64 : 1;
        ItemStack[] matrixNow = matrix();
        ItemStack result = AugmentRecipeService.matchCrafting(matrixNow, player.getWorld());
        if (result == null || result.getType().isAir()) {
            craftingBusy.set(false);
            player.sendMessage(LegacyColors.color("#FF5555No valid recipe, missing items, or output blocked."));
            return;
        }

        ItemStack[] templates = new ItemStack[9];
        String[] keys = new String[9];
        int[] gridAmounts = new int[9];
        Map<String, Integer> needPerCraft = new LinkedHashMap<>();
        Map<String, Integer> gridByKey = new HashMap<>();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = matrixNow[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemStack template = stack.clone();
            template.setAmount(1);
            templates[i] = template;
            String key = StoredStack.itemKey(template);
            keys[i] = key;
            gridAmounts[i] = stack.getAmount();
            needPerCraft.merge(key, 1, Integer::sum);
            gridByKey.merge(key, stack.getAmount(), Integer::sum);
        }

        if (needPerCraft.isEmpty()) {
            craftingBusy.set(false);
            player.sendMessage(LegacyColors.color("#FF5555No valid recipe, missing items, or output blocked."));
            return;
        }

        // Snapshot network availability on the main thread (linked chests are Bukkit inventories).
        Map<String, Long> networkAvail = new HashMap<>();
        for (String key : needPerCraft.keySet()) {
            networkAvail.put(key, network.getCombinedAmount(key));
        }

        ItemStack resultTemplate = result.clone();
        boolean toNetwork = sendToNetwork;
        int outputAmount = outputBuffer == null || outputBuffer.getType().isAir()
                ? 0
                : outputBuffer.getAmount();
        ItemStack outputCopy = outputBuffer == null || outputBuffer.getType().isAir()
                ? null
                : outputBuffer.clone();
        int capacity = network.getCapacity();
        long virtualUsed = network.getTotalItems();
        boolean hasLinkedSpace = toNetwork && gg.drak.restored.util.LinkedChestStorage.hasAnyFreeSlot(network);

        Map<String, Integer> needSnapshot = Map.copyOf(needPerCraft);
        Map<String, Integer> gridSnapshot = Map.copyOf(gridByKey);
        ItemStack[] templatesSnap = templates;
        String[] keysSnap = keys;
        int[] gridAmountsSnap = gridAmounts.clone();

        if (maxRequested <= 1) {
            try {
                int crafts = planCraftCount(
                        maxRequested,
                        needSnapshot,
                        gridSnapshot,
                        networkAvail,
                        resultTemplate,
                        toNetwork,
                        outputCopy,
                        outputAmount,
                        capacity,
                        virtualUsed,
                        hasLinkedSpace
                );
                int done = crafts <= 0 ? 0 : applyBulkCraft(
                        crafts, templatesSnap, keysSnap, gridAmountsSnap,
                        needSnapshot, gridSnapshot, resultTemplate
                );
                if (done > 0) {
                    player.sendMessage(LegacyColors.color("#00FC88Crafted x" + done + "."));
                    network.save();
                } else {
                    player.sendMessage(LegacyColors.color(
                            "#FF5555No valid recipe, missing items, or output blocked."));
                }
            } finally {
                craftingBusy.set(false);
                render();
            }
            return;
        }

        // Shift-click: plan off-thread from the snapshot, then mutate on the main thread.
        render(); // show "Crafting in progress…"
        Bukkit.getScheduler().runTaskAsynchronously(Restored.getInstance(), () -> {
            int crafts = planCraftCount(
                    maxRequested,
                    needSnapshot,
                    gridSnapshot,
                    networkAvail,
                    resultTemplate,
                    toNetwork,
                    outputCopy,
                    outputAmount,
                    capacity,
                    virtualUsed,
                    hasLinkedSpace
            );
            Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                try {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (crafts <= 0) {
                        player.sendMessage(LegacyColors.color(
                                "#FF5555No valid recipe, missing items, or output blocked."));
                        return;
                    }
                    int done = applyBulkCraft(
                            crafts, templatesSnap, keysSnap, gridAmountsSnap,
                            needSnapshot, gridSnapshot, resultTemplate
                    );
                    if (done > 0) {
                        player.sendMessage(LegacyColors.color("#00FC88Crafted x" + done + "."));
                        network.save();
                    } else {
                        player.sendMessage(LegacyColors.color(
                                "#FF5555No valid recipe, missing items, or output blocked."));
                    }
                } finally {
                    craftingBusy.set(false);
                    if (player.isOnline()) {
                        render();
                    }
                }
            });
        });
    }

    private static int planCraftCount(
            int maxRequested,
            Map<String, Integer> needPerCraft,
            Map<String, Integer> gridByKey,
            Map<String, Long> networkAvail,
            ItemStack result,
            boolean toNetwork,
            ItemStack outputCopy,
            int outputAmount,
            int capacity,
            long virtualUsed,
            boolean hasLinkedSpace
    ) {
        int maxByIng = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : needPerCraft.entrySet()) {
            int need = entry.getValue();
            if (need <= 0) {
                continue;
            }
            long grid = gridByKey.getOrDefault(entry.getKey(), 0);
            long net = networkAvail.getOrDefault(entry.getKey(), 0L);
            maxByIng = Math.min(maxByIng, (int) ((grid + net) / need));
        }
        if (maxByIng == Integer.MAX_VALUE) {
            maxByIng = 0;
        }

        int perResult = Math.max(1, result.getAmount());
        int maxByOut;
        if (toNetwork) {
            long virtualSpace = Math.max(0, capacity - virtualUsed);
            // Linked chests can accept overflow beyond virtual capacity.
            maxByOut = hasLinkedSpace
                    ? maxRequested
                    : (int) Math.min(maxRequested, virtualSpace / perResult);
        } else {
            int maxStack = result.getMaxStackSize();
            if (outputCopy == null || outputCopy.getType().isAir()) {
                maxByOut = Math.min(maxRequested, maxStack / perResult);
            } else if (!outputCopy.isSimilar(result)) {
                maxByOut = 0;
            } else {
                maxByOut = Math.min(maxRequested, (maxStack - outputAmount) / perResult);
            }
        }

        return Math.max(0, Math.min(maxRequested, Math.min(maxByIng, maxByOut)));
    }

    /**
     * Consumes ingredients and deposits results in bulk (one extract/insert per item key).
     * Leaves one ingredient in each grid slot when network stock allows (same as the old loop).
     */
    private int applyBulkCraft(
            int crafts,
            ItemStack[] templates,
            String[] keys,
            int[] gridAmounts,
            Map<String, Integer> needPerCraft,
            Map<String, Integer> gridByKey,
            ItemStack resultTemplate
    ) {
        if (crafts <= 0) {
            return 0;
        }

        // Re-check live availability in case something changed while planning.
        for (Map.Entry<String, Integer> entry : needPerCraft.entrySet()) {
            long need = (long) crafts * entry.getValue();
            long grid = gridByKey.getOrDefault(entry.getKey(), 0);
            long net = network.getCombinedAmount(entry.getKey());
            if (grid + net < need) {
                crafts = (int) ((grid + net) / entry.getValue());
            }
        }
        if (crafts <= 0) {
            return 0;
        }

        if (!canAcceptResultTimes(resultTemplate, crafts)) {
            // Shrink to what output can take.
            while (crafts > 0 && !canAcceptResultTimes(resultTemplate, crafts)) {
                crafts--;
            }
            if (crafts <= 0) {
                return 0;
            }
        }

        Map<String, Long> extractFromNetwork = new HashMap<>();
        for (Map.Entry<String, Integer> entry : needPerCraft.entrySet()) {
            long need = (long) crafts * entry.getValue();
            long fromGrid = gridByKey.getOrDefault(entry.getKey(), 0);
            long fromNet = Math.max(0, need - fromGrid);
            if (fromNet > 0) {
                extractFromNetwork.put(entry.getKey(), fromNet);
            }
        }
        // End-of-batch refill (1 per occupied slot) to mirror previous per-craft refill behavior.
        Map<String, Long> refillExtra = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            if (keys[i] == null) {
                continue;
            }
            refillExtra.merge(keys[i], 1L, Long::sum);
        }
        for (Map.Entry<String, Long> entry : refillExtra.entrySet()) {
            extractFromNetwork.merge(entry.getKey(), entry.getValue(), Long::sum);
        }

        Map<String, Long> extracted = new HashMap<>();
        for (Map.Entry<String, Long> entry : extractFromNetwork.entrySet()) {
            long taken = network.extract(entry.getKey(), entry.getValue());
            extracted.put(entry.getKey(), taken);
        }

        // Prefer grid for crafts; leftover extracted stock covers refills.
        Map<String, Long> leftoverExtracted = new HashMap<>(extracted);
        for (Map.Entry<String, Integer> entry : needPerCraft.entrySet()) {
            long needFromNet = Math.max(0, (long) crafts * entry.getValue() - gridByKey.getOrDefault(entry.getKey(), 0));
            leftoverExtracted.merge(entry.getKey(), -needFromNet, Long::sum);
        }

        for (int i = 0; i < 9; i++) {
            if (keys[i] == null) {
                continue;
            }
            int remainingInSlot = gridAmounts[i] - crafts;
            if (remainingInSlot > 0) {
                ItemStack left = templates[i].clone();
                left.setAmount(remainingInSlot);
                craftSlots.put(i, left);
                continue;
            }
            // Slot emptied by crafts — place a 1-stack refill if we still have extracted stock.
            long left = leftoverExtracted.getOrDefault(keys[i], 0L);
            if (left > 0) {
                leftoverExtracted.put(keys[i], left - 1);
                ItemStack refill = templates[i].clone();
                refill.setAmount(1);
                craftSlots.put(i, refill);
            } else {
                craftSlots.remove(i);
            }
        }

        // Any unused extracted items (failed refill accounting) go back into the network.
        for (Map.Entry<String, Long> entry : leftoverExtracted.entrySet()) {
            long left = entry.getValue();
            if (left <= 0) {
                continue;
            }
            ItemStack template = null;
            for (int i = 0; i < 9; i++) {
                if (entry.getKey().equals(keys[i])) {
                    template = templates[i];
                    break;
                }
            }
            if (template != null) {
                network.insert(template, left);
            }
        }

        if (!depositResultsBulk(resultTemplate, crafts)) {
            // Extremely unlikely after canAccept check — refund is not perfect; report failure count.
            return 0;
        }
        return crafts;
    }

    private boolean canAcceptResultTimes(ItemStack result, int times) {
        if (result == null || result.getType().isAir() || times <= 0) {
            return false;
        }
        int per = Math.max(1, result.getAmount());
        long total = (long) per * times;
        if (sendToNetwork) {
            if (network.getTotalItems() + total <= network.getCapacity()) {
                return true;
            }
            // Virtual full — still OK if linked chests can take items.
            return gg.drak.restored.util.LinkedChestStorage.hasLinkedSpace(network, result)
                    || network.getTotalItems() < network.getCapacity();
        }
        if (outputBuffer == null || outputBuffer.getType().isAir()) {
            return total <= result.getMaxStackSize();
        }
        if (!outputBuffer.isSimilar(result)) {
            return false;
        }
        return outputBuffer.getAmount() + total <= outputBuffer.getMaxStackSize();
    }

    private boolean depositResultsBulk(ItemStack resultTemplate, int times) {
        if (resultTemplate == null || times <= 0) {
            return false;
        }
        int per = Math.max(1, resultTemplate.getAmount());
        long total = (long) per * times;
        if (sendToNetwork) {
            long inserted = network.insert(resultTemplate, total);
            if (inserted >= total) {
                return true;
            }
            long remain = total - inserted;
            while (remain > 0) {
                int batch = (int) Math.min(remain, resultTemplate.getMaxStackSize());
                ItemStack give = resultTemplate.clone();
                give.setAmount(batch);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        long restored = network.insert(drop, drop.getAmount());
                        if (restored < drop.getAmount()) {
                            ItemStack unrecovered = drop.clone();
                            unrecovered.setAmount((int) (drop.getAmount() - restored));
                            player.getWorld().dropItemNaturally(player.getLocation(), unrecovered);
                        }
                    }
                    player.sendMessage(LegacyColors.color("#FF5555Network and inventory are full."));
                }
                remain -= batch;
            }
            return true;
        }

        if (outputBuffer == null || outputBuffer.getType().isAir()) {
            ItemStack buf = resultTemplate.clone();
            int placed = (int) Math.min(total, resultTemplate.getMaxStackSize());
            buf.setAmount(placed);
            outputBuffer = buf;
            if (placed < total) {
                giveOrDropAmount(resultTemplate, total - placed);
            }
            return true;
        }
        if (!outputBuffer.isSimilar(resultTemplate)) {
            giveOrDropAmount(resultTemplate, total);
            player.sendMessage(LegacyColors.color("#FF5555Output slot changed; the result was returned."));
            return true;
        }
        int add = (int) Math.min(total, outputBuffer.getMaxStackSize() - outputBuffer.getAmount());
        if (add > 0) {
            outputBuffer.setAmount(outputBuffer.getAmount() + add);
        }
        if (add < total) {
            giveOrDropAmount(resultTemplate, total - add);
        }
        return true;
    }

    private void giveOrDropAmount(ItemStack template, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int batch = (int) Math.min(remaining, template.getMaxStackSize());
            ItemStack give = template.clone();
            give.setAmount(batch);
            giveOrDrop(give);
            remaining -= batch;
        }
    }

    private ItemStack[] matrix() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = craftSlots.get(i);
        }
        return matrix;
    }
}
