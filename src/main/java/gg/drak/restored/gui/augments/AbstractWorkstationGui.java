package gg.drak.restored.gui.augments;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import host.plas.bou.gui.editor.EditorDragDrop;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.data.WorkstationSession;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractWorkstationGui extends AbstractInventoryGui {
    private static final int BACK_SLOT = 4;
    private static final int[] DEPOSIT_INV_SLOTS = {46, 47, 48, 49, 50, 51};
    private static final int DEPOSIT_CONFIRM_SLOT = 52;

    protected final Network network;
    protected final AugmentType augmentType;
    protected final Map<Integer, ItemStack> craftSlots = new HashMap<>();
    /** Staging row along the bottom — only sent to the network when confirmed. */
    private final ItemStack[] depositBuffer = new ItemStack[DEPOSIT_INV_SLOTS.length];
    /** Physical output buffer when not sending results to the network. */
    protected ItemStack outputBuffer;
    /** true = green pane (network), false = gray pane (output slot). */
    protected boolean sendToNetwork = true;
    private boolean returningSlots;

    protected AbstractWorkstationGui(Player player, Network network, AugmentType augmentType) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
        this.augmentType = augmentType;
        loadSession();
    }

    private void loadSession() {
        WorkstationSession session = network.getOrCreateWorkstationSession(augmentType);
        craftSlots.clear();
        for (Map.Entry<Integer, ItemStack> entry : session.getCraftSlots().entrySet()) {
            ItemStack stack = entry.getValue();
            if (!isCraftSlotEmpty(stack)) {
                craftSlots.put(entry.getKey(), stack.clone());
            }
        }
        ItemStack[] savedDeposit = session.getDepositBuffer();
        for (int i = 0; i < depositBuffer.length; i++) {
            if (savedDeposit != null && i < savedDeposit.length
                    && savedDeposit[i] != null && !savedDeposit[i].getType().isAir()) {
                depositBuffer[i] = savedDeposit[i].clone();
            } else {
                depositBuffer[i] = null;
            }
        }
        ItemStack savedOutput = session.getOutputBuffer();
        outputBuffer = savedOutput == null || savedOutput.getType().isAir() ? null : savedOutput.clone();
        sendToNetwork = session.isSendToNetwork();
    }

    private void saveSession() {
        WorkstationSession session = network.getOrCreateWorkstationSession(augmentType);
        session.getCraftSlots().clear();
        for (Map.Entry<Integer, ItemStack> entry : craftSlots.entrySet()) {
            ItemStack stack = entry.getValue();
            if (!isCraftSlotEmpty(stack)) {
                session.getCraftSlots().put(entry.getKey(), stack.clone());
            }
        }
        ItemStack[] savedDeposit = new ItemStack[depositBuffer.length];
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack stack = depositBuffer[i];
            savedDeposit[i] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }
        session.setDepositBuffer(savedDeposit);
        session.setOutputBuffer(
                outputBuffer == null || outputBuffer.getType().isAir() ? null : outputBuffer.clone()
        );
        session.setSendToNetwork(sendToNetwork);
        saveExtraSession(session);
    }

    /** Hook for workstation-specific in-memory settings. */
    protected void saveExtraSession(WorkstationSession session) {
    }

    protected abstract void renderContents(ItemStack[] contents);

    protected abstract void onWorkstationClick(ClickType clickType);

    /** Inventory slot for the output buffer. Override to reposition. */
    protected int outputBufferInvSlot() {
        return 34;
    }

    /** Inventory slot for the network/slot destination toggle. */
    protected int outputToggleInvSlot() {
        return 33;
    }

    protected ItemStack emptyCraftPane(String label) {
        return GuiItems.button(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "#AAAAAA" + label,
                List.of(
                        "#bdc8c9Click to select from the network.",
                        "#bdc8c9Or place an item from your inventory / cursor."
                )
        );
    }

    protected ItemStack displaySlot(int slotId, String emptyLabel) {
        ItemStack stack = craftSlots.get(slotId);
        if (isCraftSlotEmpty(stack)) {
            if (stack != null) {
                craftSlots.remove(slotId);
            }
            return emptyCraftPane(emptyLabel);
        }
        return stack.clone();
    }

    protected void bindCraftSlot(int inventorySlot, int slotId) {
        bindSlot(inventorySlot, "craft:" + slotId);
    }

    protected void bindWorkstation(int inventorySlot) {
        bindSlot(inventorySlot, "workstation");
    }

    protected ItemStack workstationButton(List<String> extraLore) {
        List<String> lore = List.of(
                "#bdc8c9Left-click: process once",
                "#bdc8c9Shift-left: process a stack",
                "#bdc8c9Right-click: clear craft slots back to network"
        );
        if (extraLore != null && !extraLore.isEmpty()) {
            java.util.ArrayList<String> merged = new java.util.ArrayList<>(extraLore);
            merged.add("");
            merged.addAll(lore);
            lore = merged;
        }
        return GuiItems.button(
                augmentType.getWorkstationMaterial(),
                "#FFED6A&l" + augmentType.getDisplayName(),
                lore
        );
    }

    protected void placeOutputChrome(ItemStack[] contents) {
        int toggleSlot = outputToggleInvSlot();
        int bufferSlot = outputBufferInvSlot();
        if (toggleSlot >= 0 && toggleSlot < contents.length) {
            contents[toggleSlot] = GuiItems.button(
                    sendToNetwork ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    sendToNetwork ? "#00FC88&lOutput: Network" : "#AAAAAA&lOutput: Slot",
                    List.of(
                            sendToNetwork
                                    ? "#bdc8c9Results go into the network."
                                    : "#bdc8c9Results go into the output slot.",
                            "",
                            "#bdc8c9Click to toggle."
                    )
            );
            bindSlot(toggleSlot, "output_toggle");
        }
        if (bufferSlot >= 0 && bufferSlot < contents.length) {
            if (outputBuffer != null && !outputBuffer.getType().isAir()) {
                contents[bufferSlot] = withLore(outputBuffer, List.of(
                        "#AAAAAAOutput slot",
                        "#bdc8c9Click to take into your inventory."
                ));
            } else {
                contents[bufferSlot] = GuiItems.button(
                        Material.BLACK_STAINED_GLASS_PANE,
                        "#AAAAAAOutput Slot",
                        List.of(
                                "#bdc8c9Crafted items land here when",
                                "#bdc8c9output mode is set to Slot."
                        )
                );
            }
            bindSlot(bufferSlot, "output_buffer");
        }
    }

    protected void placeDepositRow(ItemStack[] contents) {
        for (int i = 0; i < DEPOSIT_INV_SLOTS.length; i++) {
            int invSlot = DEPOSIT_INV_SLOTS[i];
            ItemStack stacked = depositBuffer[i];
            if (stacked != null && !stacked.getType().isAir()) {
                contents[invSlot] = stacked.clone();
            } else {
                contents[invSlot] = GuiItems.button(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "#AAAAAADeposit Slot",
                        List.of(
                                "#bdc8c9Place items here, then press",
                                "#FFED6AConfirm Deposit #bdc8c9to send them",
                                "#bdc8c9into the network."
                        )
                );
            }
            bindSlot(invSlot, "deposit:" + i);
        }
        contents[DEPOSIT_CONFIRM_SLOT] = GuiItems.button(
                Material.OAK_BUTTON,
                "#FFED6A&lConfirm Deposit",
                List.of(
                        "#bdc8c9Send all items in this row",
                        "#bdc8c9into the network.",
                        "",
                        "#AAAAAAClosing without confirming",
                        "#AAAAAAreturns them to you."
                )
        );
        bindSlot(DEPOSIT_CONFIRM_SLOT, "deposit_confirm");
    }

    @Override
    public void open() {
        render();
    }

    protected void render() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&l" + augmentType.getDisplayName() + " Augment");
        renderContents(contents);
        placeOutputChrome(contents);
        placeDepositRow(contents);
        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().equals(player.getInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) {
                    return;
                }
                int placed = handleSpecialShiftClick(clicked);
                if (placed <= 0) {
                    placed = placeIntoDepositRow(clicked);
                }
                if (placed <= 0) {
                    placed = placeIntoCraftSlots(clicked);
                }
                if (placed > 0) {
                    if (clicked.getAmount() <= 0) {
                        event.setCurrentItem(null);
                    }
                    render();
                }
            }
            return;
        }

        if (!event.getClickedInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("back".equals(key)) {
            returningSlots = true;
            saveSession();
            new AugmentsListGui(player, network).open();
            return;
        }
        if ("output_toggle".equals(key)) {
            sendToNetwork = !sendToNetwork;
            render();
            return;
        }
        if ("output_buffer".equals(key)) {
            takeOutputBuffer();
            return;
        }
        if ("deposit_confirm".equals(key)) {
            confirmDepositRow();
            return;
        }
        if (key.startsWith("deposit:")) {
            int index = Integer.parseInt(key.substring("deposit:".length()));
            handleDepositSlotClick(index, event);
            return;
        }
        if (key.startsWith("craft:")) {
            int slotId = Integer.parseInt(key.substring("craft:".length()));
            handleCraftSlotClick(slotId, event);
            return;
        }
        if ("workstation".equals(key)) {
            ClickType click = event.getClick();
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                returnSlotsToNetwork();
                saveSession();
                render();
                player.sendMessage(LegacyColors.color("#00FC88Cleared crafting slots."));
                return;
            }
            onWorkstationClick(click);
            return;
        }
        handleExtraClick(key, event);
    }

    /** Subclasses may block craft-slot edits while processing (e.g. cooking). */
    protected boolean allowCraftSlotEdit() {
        return true;
    }

    private void handleCraftSlotClick(int slotId, InventoryClickEvent event) {
        if (!allowCraftSlotEdit()) {
            player.sendMessage(LegacyColors.color("#FF5555Cannot change slots right now."));
            return;
        }

        // Authoritative cursor — event.getCursor() can be stale after cancel on some platforms.
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            cursor = EditorDragDrop.cursorItem(event);
        }

        ItemStack existing = craftSlots.get(slotId);
        // Empty = nothing stored, or the gray/light-gray empty marker pane.
        boolean empty = isCraftSlotEmpty(existing);
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        if (hasCursor) {
            if (event.isRightClick()) {
                // Right-click: place exactly one if empty; add one if same; else swap stacks.
                if (empty) {
                    ItemStack one = cursor.clone();
                    one.setAmount(1);
                    craftSlots.put(slotId, one);
                    decrementCursor(cursor, 1);
                    render();
                    return;
                }
                if (existing.isSimilar(cursor)) {
                    if (existing.getAmount() >= existing.getMaxStackSize()) {
                        player.sendMessage(LegacyColors.color("#FF5555That craft slot is full."));
                        return;
                    }
                    existing.setAmount(existing.getAmount() + 1);
                    craftSlots.put(slotId, existing);
                    decrementCursor(cursor, 1);
                    render();
                    return;
                }
                craftSlots.put(slotId, cursor.clone());
                player.setItemOnCursor(existing.clone());
                render();
                return;
            }

            // Left-click with cursor: place whole stack / merge / swap.
            placeAllFromCursor(slotId, empty ? null : existing, !empty, cursor, event);
            return;
        }

        if (!empty) {
            // Pick up the item onto the cursor (empty gray pane opens picker instead).
            craftSlots.remove(slotId);
            player.setItemOnCursor(existing.clone());
            render();
            return;
        }

        // Empty gray pane, no cursor → network picker.
        craftSlots.remove(slotId);
        openPicker(slotId);
    }

    /** True when the slot has no real item (air/null or empty gray pane marker). */
    private static boolean isCraftSlotEmpty(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return true;
        }
        Material type = stack.getType();
        return type == Material.LIGHT_GRAY_STAINED_GLASS_PANE
                || type == Material.GRAY_STAINED_GLASS_PANE;
    }

    private void decrementCursor(ItemStack cursor, int amount) {
        int left = cursor.getAmount() - amount;
        if (left <= 0) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(left);
            player.setItemOnCursor(cursor);
        }
    }

    /** Left-click with cursor: place the whole cursor stack (merge if same, swap if different). */
    private void placeAllFromCursor(
            int slotId,
            ItemStack existing,
            boolean hasExisting,
            ItemStack cursor,
            InventoryClickEvent event
    ) {
        if (!hasExisting) {
            craftSlots.put(slotId, cursor.clone());
            player.setItemOnCursor(null);
            render();
            return;
        }
        if (existing.isSimilar(cursor)) {
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                player.sendMessage(LegacyColors.color("#FF5555That craft slot is full."));
                return;
            }
            int move = Math.min(space, cursor.getAmount());
            existing.setAmount(existing.getAmount() + move);
            craftSlots.put(slotId, existing);
            decrementCursor(cursor, move);
            render();
            return;
        }
        craftSlots.put(slotId, cursor.clone());
        player.setItemOnCursor(existing.clone());
        render();
    }

    private void handleDepositSlotClick(int index, InventoryClickEvent event) {
        if (index < 0 || index >= depositBuffer.length) {
            return;
        }
        ItemStack cursor = EditorDragDrop.cursorItem(event);
        ItemStack existing = depositBuffer[index];
        boolean hasExisting = existing != null && !existing.getType().isAir();
        boolean hasCursor = cursor != null;

        if (hasCursor) {
            if (!hasExisting) {
                depositBuffer[index] = cursor.clone();
                event.getView().setCursor(null);
                render();
                return;
            }
            if (existing.isSimilar(cursor)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) {
                    return;
                }
                int move = Math.min(space, cursor.getAmount());
                existing.setAmount(existing.getAmount() + move);
                depositBuffer[index] = existing;
                cursor.setAmount(cursor.getAmount() - move);
                if (cursor.getAmount() <= 0) {
                    event.getView().setCursor(null);
                }
                render();
                return;
            }
            depositBuffer[index] = cursor.clone();
            event.getView().setCursor(existing.clone());
            render();
            return;
        }

        if (hasExisting) {
            depositBuffer[index] = null;
            event.getView().setCursor(existing.clone());
            render();
        }
    }

    private void confirmDepositRow() {
        if (!network.canDeposit(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot deposit into this network."));
            return;
        }
        long total = 0;
        long leftoverTotal = 0;
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack stack = depositBuffer[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            long inserted = network.insert(stack, stack.getAmount());
            total += inserted;
            long left = stack.getAmount() - inserted;
            if (left <= 0) {
                depositBuffer[i] = null;
            } else {
                stack.setAmount((int) left);
                depositBuffer[i] = stack;
                leftoverTotal += left;
            }
        }
        if (total > 0) {
            network.save();
            player.sendMessage(LegacyColors.color("#00FC88Deposited #FFED6A" + total + " #00FC88item(s) into the network."));
        }
        if (leftoverTotal > 0) {
            player.sendMessage(LegacyColors.color("#FF5555Network full — #FFED6A" + leftoverTotal + " #FF5555item(s) remain in the row."));
        } else if (total <= 0) {
            player.sendMessage(LegacyColors.color("#FF5555Nothing to deposit."));
        }
        render();
    }

    private int placeIntoDepositRow(ItemStack fromPlayer) {
        if (fromPlayer == null || fromPlayer.getType().isAir()) {
            return 0;
        }
        int moved = 0;
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack existing = depositBuffer[i];
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(fromPlayer)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, fromPlayer.getAmount());
            existing.setAmount(existing.getAmount() + move);
            depositBuffer[i] = existing;
            fromPlayer.setAmount(fromPlayer.getAmount() - move);
            moved += move;
            if (fromPlayer.getAmount() <= 0) {
                return moved;
            }
        }
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack existing = depositBuffer[i];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            depositBuffer[i] = fromPlayer.clone();
            moved += fromPlayer.getAmount();
            fromPlayer.setAmount(0);
            return moved;
        }
        return moved;
    }

    private void returnDepositBufferToPlayer() {
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack stack = depositBuffer[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            depositBuffer[i] = null;
        }
    }

    /**
     * Shift-click from player inventory: fill matching or first empty craft slot.
     * @return amount moved
     */
    protected int placeIntoCraftSlots(ItemStack fromPlayer) {
        if (fromPlayer == null || fromPlayer.getType().isAir()) {
            return 0;
        }
        int moved = 0;
        for (Integer slotId : craftSlotIds()) {
            ItemStack existing = craftSlots.get(slotId);
            if (isCraftSlotEmpty(existing) || !existing.isSimilar(fromPlayer)) {
                continue;
            }
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, fromPlayer.getAmount());
            existing.setAmount(existing.getAmount() + move);
            craftSlots.put(slotId, existing);
            fromPlayer.setAmount(fromPlayer.getAmount() - move);
            moved += move;
            if (fromPlayer.getAmount() <= 0) {
                return moved;
            }
        }
        for (Integer slotId : craftSlotIds()) {
            ItemStack existing = craftSlots.get(slotId);
            if (!isCraftSlotEmpty(existing)) {
                continue;
            }
            ItemStack placed = fromPlayer.clone();
            craftSlots.put(slotId, placed);
            moved += fromPlayer.getAmount();
            fromPlayer.setAmount(0);
            return moved;
        }
        return moved;
    }

    /** Workstations may consume a shift-clicked item into a non-craft setting. */
    protected int handleSpecialShiftClick(ItemStack fromPlayer) {
        return 0;
    }

    /**
     * Known craft slot ids for inventory placement. Defaults to whatever is currently occupied
     * plus common 0–8; subclasses with fixed ids should override.
     */
    protected Iterable<Integer> craftSlotIds() {
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 9; i++) {
            ids.add(i);
        }
        ids.addAll(craftSlots.keySet());
        return ids;
    }

    protected void handleExtraClick(String key, InventoryClickEvent event) {
        // subclasses
    }

    protected void openPicker(int slotId) {
        int maxAmount = maxPickAmount(slotId);
        returningSlots = true;
        saveSession();
        new AugmentItemPickerGui(player, network, augmentType, slotId, maxAmount, extracted -> {
            loadSession();
            if (extracted != null) {
                ItemStack existing = craftSlots.get(slotId);
                if (existing != null && !existing.getType().isAir()) {
                    if (existing.isSimilar(extracted)) {
                        int space = existing.getMaxStackSize() - existing.getAmount();
                        int move = Math.min(space, extracted.getAmount());
                        existing.setAmount(existing.getAmount() + move);
                        craftSlots.put(slotId, existing);
                        if (move < extracted.getAmount()) {
                            ItemStack leftover = extracted.clone();
                            leftover.setAmount(extracted.getAmount() - move);
                            network.insert(leftover, leftover.getAmount());
                            network.save();
                        }
                    } else {
                        // Keep existing on cursor path isn't available here — put extracted in slot
                        // and return the previous item to the player.
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(existing.clone());
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                        craftSlots.put(slotId, extracted);
                    }
                } else {
                    craftSlots.put(slotId, extracted);
                }
            }
            saveSession();
            returningSlots = false;
            open();
        }).open();
    }

    protected int maxPickAmount(int slotId) {
        return 64;
    }

    protected void returnSlotsToNetwork() {
        for (Map.Entry<Integer, ItemStack> entry : craftSlots.entrySet()) {
            ItemStack stack = entry.getValue();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            long inserted = network.insert(stack, stack.getAmount());
            if (inserted < stack.getAmount()) {
                ItemStack leftover = stack.clone();
                leftover.setAmount((int) (stack.getAmount() - inserted));
                player.getInventory().addItem(leftover);
            }
        }
        craftSlots.clear();
        if (outputBuffer != null && !outputBuffer.getType().isAir()) {
            long inserted = network.insert(outputBuffer, outputBuffer.getAmount());
            if (inserted < outputBuffer.getAmount()) {
                ItemStack leftover = outputBuffer.clone();
                leftover.setAmount((int) (outputBuffer.getAmount() - inserted));
                player.getInventory().addItem(leftover);
            }
            outputBuffer = null;
        }
        network.save();
    }

    protected void takeOutputBuffer() {
        if (outputBuffer == null || outputBuffer.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(outputBuffer.clone());
        if (leftover.isEmpty()) {
            outputBuffer = null;
        } else {
            outputBuffer = leftover.values().iterator().next();
            player.sendMessage(LegacyColors.color("#FF5555Your inventory is full."));
        }
        render();
    }

    /**
     * After consuming ingredients, pull replacements from the network for emptied slots.
     */
    protected void refillFromNetwork(int slotId, ItemStack template, int amount) {
        if (template == null || amount <= 0) {
            return;
        }
        String key = StoredStack.itemKey(template);
        long taken = network.extract(key, amount);
        if (taken <= 0) {
            craftSlots.remove(slotId);
            return;
        }
        ItemStack refill = template.clone();
        refill.setAmount((int) taken);
        craftSlots.put(slotId, refill);
    }

    /**
     * Whether a result can be deposited without exceeding the output slot stack size.
     * Prints a chat message and returns false when blocked.
     */
    protected boolean canAcceptResult(ItemStack result) {
        if (result == null || result.getType().isAir()) {
            return false;
        }
        if (sendToNetwork) {
            if (!network.canInsert(result, result.getAmount()) && network.getTotalItems() >= network.getCapacity()) {
                player.sendMessage(LegacyColors.color("#FF5555Network is full — cannot craft."));
                return false;
            }
            return true;
        }
        if (outputBuffer == null || outputBuffer.getType().isAir()) {
            return result.getAmount() <= result.getMaxStackSize();
        }
        if (!outputBuffer.isSimilar(result)) {
            player.sendMessage(LegacyColors.color("#FF5555Output slot has a different item — clear it first."));
            return false;
        }
        int max = outputBuffer.getMaxStackSize();
        if (outputBuffer.getAmount() + result.getAmount() > max) {
            player.sendMessage(LegacyColors.color(
                    "#FF5555Crafting would exceed the output stack size (" + max + ")."));
            return false;
        }
        return true;
    }

    protected boolean depositResult(ItemStack result) {
        if (result == null || result.getType().isAir()) {
            return false;
        }
        if (!canAcceptResult(result)) {
            return false;
        }
        if (sendToNetwork) {
            long inserted = network.insert(result, result.getAmount());
            if (inserted >= result.getAmount()) {
                network.save();
                return true;
            }
            if (inserted > 0) {
                result.setAmount((int) (result.getAmount() - inserted));
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
            network.save();
            if (!leftover.isEmpty()) {
                player.sendMessage(LegacyColors.color("#FF5555Network and inventory are full."));
                for (ItemStack drop : leftover.values()) {
                    network.insert(drop, drop.getAmount());
                }
                return false;
            }
            return true;
        }

        if (outputBuffer == null || outputBuffer.getType().isAir()) {
            outputBuffer = result.clone();
        } else {
            outputBuffer.setAmount(outputBuffer.getAmount() + result.getAmount());
        }
        return true;
    }

    protected static ItemStack withLore(ItemStack stack, List<String> lore) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> colored = new java.util.ArrayList<>();
            for (String line : lore) {
                colored.add(LegacyColors.color(line));
            }
            meta.setLore(colored);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!returningSlots) {
            saveSession();
        }
        returningSlots = false;
    }

    protected void markKeepSlots() {
        returningSlots = true;
    }
}
