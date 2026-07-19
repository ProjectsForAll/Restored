package gg.drak.restored.data.blocks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.UpgradeHolder;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.impl.ExternalStorageItem;
import gg.drak.restored.data.screens.items.StoredItem;
import gg.drak.restored.gui.NetworkGuiScreenInstance;
import host.plas.bou.gui.screens.events.BlockCloseEvent;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.items.ItemUtils;
import host.plas.bou.utils.ColorUtils;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Connects to an adjacent container and exposes its contents as network storage.
 * The container's inventory is treated as part of the network — items can be
 * inserted into and extracted from it through the network viewer.
 */
@Getter @Setter
public class ExternalStorage extends NetworkBlock implements UpgradeHolder, InventoryBlock {
    private static final int[] UPGRADE_SLOTS = {4, 5, 6, 7, 8};

    private static final int PRIORITY_SLOT = 2;

    private List<ItemStack> upgradeCards = new ArrayList<>();
    private int priority = 0;

    public ExternalStorage(Network network, Location location) {
        super(BlockType.EXTERNAL_STORAGE, network, location, ExternalStorageItem::new);
        onLoad();
    }

    public ExternalStorage(UUID uuid, Network network, Location location, JsonObject data) {
        super(BlockType.EXTERNAL_STORAGE, uuid, network, location, ExternalStorageItem::new, data);
        onLoad();
    }

    @Override
    public void onLoad() {
        upgradeCards = new ArrayList<>();
        JsonObject data = getData();
        if (data.has("priority")) {
            this.priority = data.get("priority").getAsInt();
        }
        if (data.has("upgrades")) {
            JsonArray arr = data.getAsJsonArray("upgrades");
            for (int i = 0; i < arr.size(); i++) {
                String typeName = arr.get(i).getAsString();
                if (typeName.equals("SPEED_CARD")) {
                    upgradeCards.add(new gg.drak.restored.data.items.impl.SpeedCardItem().getItem());
                } else if (typeName.equals("STACK_CARD")) {
                    upgradeCards.add(new gg.drak.restored.data.items.impl.StackCardItem().getItem());
                }
            }
        }
    }

    @Override
    public void onSaveSpecific() {
        getData().addProperty("priority", priority);
        JsonArray arr = new JsonArray();
        for (ItemStack card : upgradeCards) {
            if (card != null) {
                ItemType type = ItemManager.getTypeFrom(card);
                if (type == ItemType.SPEED_CARD || type == ItemType.STACK_CARD) {
                    arr.add(type.name());
                }
            }
        }
        getData().add("upgrades", arr);
    }

    /**
     * Find the first adjacent container block, skipping other network blocks.
     */
    public Optional<Container> getAdjacentContainer() {
        return gg.drak.restored.data.NetworkManager.getAdjacentContainer(getBlock());
    }

    /**
     * Get items from the connected container as StoredItems.
     */
    public ConcurrentSkipListSet<StoredItem> getExternalItems() {
        ConcurrentSkipListSet<StoredItem> items = new ConcurrentSkipListSet<>();
        Optional<Container> containerOpt = getAdjacentContainer();
        if (containerOpt.isEmpty()) return items;

        Inventory inv = containerOpt.get().getInventory();
        Map<Integer, BigInteger> amountBySlot = new LinkedHashMap<>();

        // Merge similar items
        List<ItemStack> uniqueStacks = new ArrayList<>();
        List<BigInteger> amounts = new ArrayList<>();

        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            boolean found = false;
            for (int i = 0; i < uniqueStacks.size(); i++) {
                if (uniqueStacks.get(i).isSimilar(stack)) {
                    amounts.set(i, amounts.get(i).add(BigInteger.valueOf(stack.getAmount())));
                    found = true;
                    break;
                }
            }
            if (!found) {
                uniqueStacks.add(StoredItem.flattenStack(stack));
                amounts.add(BigInteger.valueOf(stack.getAmount()));
            }
        }

        for (int i = 0; i < uniqueStacks.size(); i++) {
            items.add(new StoredItem(UUID.randomUUID().toString(), amounts.get(i), uniqueStacks.get(i)));
        }

        return items;
    }

    /**
     * Insert an item into the connected container.
     */
    public int insertIntoContainer(ItemStack stack) {
        Optional<Container> containerOpt = getAdjacentContainer();
        if (containerOpt.isEmpty()) return stack.getAmount();

        Inventory inv = containerOpt.get().getInventory();
        HashMap<Integer, ItemStack> leftover = inv.addItem(stack.clone());
        if (leftover.isEmpty()) return 0;

        int remaining = 0;
        for (ItemStack left : leftover.values()) {
            remaining += left.getAmount();
        }
        return remaining;
    }

    /**
     * Remove items from the connected container.
     * @return amount still to remove (0 if fully satisfied)
     */
    public BigInteger removeFromContainer(ItemStack match, BigInteger amount) {
        Optional<Container> containerOpt = getAdjacentContainer();
        if (containerOpt.isEmpty()) return amount;

        Inventory inv = containerOpt.get().getInventory();
        BigInteger remaining = amount;

        for (int i = 0; i < inv.getSize() && remaining.compareTo(BigInteger.ZERO) > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || !slot.isSimilar(StoredItem.flattenStack(match))) continue;

            int take = remaining.min(BigInteger.valueOf(slot.getAmount())).intValue();
            if (take >= slot.getAmount()) {
                inv.setItem(i, null);
            } else {
                slot.setAmount(slot.getAmount() - take);
            }
            remaining = remaining.subtract(BigInteger.valueOf(take));
        }
        return remaining;
    }

    /**
     * Check whether at least one item could be inserted without mutating the container.
     */
    public boolean canAcceptIntoContainer(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        return simulateInsertIntoContainer(stack.clone()) < stack.getAmount();
    }

    /**
     * Simulate inserting a stack and return the amount that would remain.
     */
    public int simulateInsertIntoContainer(ItemStack stack) {
        Optional<Container> containerOpt = getAdjacentContainer();
        if (containerOpt.isEmpty()) return stack.getAmount();

        Inventory inv = containerOpt.get().getInventory();
        int remaining = stack.getAmount();

        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || !slot.isSimilar(stack)) continue;
            int space = slot.getMaxStackSize() - slot.getAmount();
            if (space <= 0) continue;
            remaining -= Math.min(space, remaining);
        }

        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && !slot.getType().isAir()) continue;
            remaining -= Math.min(stack.getMaxStackSize(), remaining);
        }

        return remaining;
    }

    @Override
    protected ScreenInstance createScreenInstance(Player player, InventorySheet inventorySheet) {
        return new NetworkGuiScreenInstance(player, getType(), inventorySheet, slot -> {
            for (int s : UPGRADE_SLOTS) {
                if (s == slot) return true;
            }
            return false;
        });
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(9);

        // Status icon showing connected container info
        Optional<Container> container = getAdjacentContainer();
        String status = container.isPresent()
                ? "&aConnected to &f" + container.get().getBlock().getType().name()
                : "&cNo container found";

        ItemStack statusItem = ItemUtils.make(Material.ENDER_EYE, "&9External Storage");
        sheet.setIcon(0, new Icon(statusItem));

        // Info slots
        ItemStack infoItem = ItemUtils.make(Material.PAPER, "&7Status", status);
        sheet.setIcon(1, new Icon(infoItem));

        // Priority control (slot 2)
        String color = priority > 0 ? "&a" : (priority < 0 ? "&c" : "&7");
        ItemStack priorityItem = ItemUtils.make(Material.COMPARATOR,
                ColorUtils.colorizeHard("&ePriority: " + color + priority),
                ColorUtils.colorizeHard("&7Left-click: &a+1"),
                ColorUtils.colorizeHard("&7Right-click: &c-1"),
                ColorUtils.colorizeHard("&7Middle-click: &eReset"));
        Icon priorityIcon = new Icon(priorityItem);
        priorityIcon.onClick(event -> {
            if (event.isLeftClick()) {
                priority++;
            } else if (event.isRightClick()) {
                priority--;
            } else {
                priority = 0;
            }
            onSave();
            redraw();
        });
        sheet.setIcon(PRIORITY_SLOT, priorityIcon);

        // Separator
        sheet.setIcon(3, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));

        // Upgrade card slots (4-8)
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            if (i < upgradeCards.size() && upgradeCards.get(i) != null) {
                sheet.setIcon(UPGRADE_SLOTS[i], new BasicIcon(upgradeCards.get(i)));
            } else {
                sheet.setIcon(UPGRADE_SLOTS[i], new BasicIcon(Material.AIR));
            }
        }

        return sheet;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&9External Storage");
    }

    public void onClose(BlockCloseEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.Inventory inv = player.getOpenInventory().getTopInventory();

        // Read upgrade cards from inventory
        upgradeCards.clear();
        for (int slot : UPGRADE_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                ItemType type = ItemManager.getTypeFrom(item);
                if (type == ItemType.SPEED_CARD || type == ItemType.STACK_CARD) {
                    upgradeCards.add(item.clone());
                }
            }
        }

        onSave();
    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;

        // Check if it's an upgrade card
        ItemType type = ItemManager.getTypeFrom(stack);
        if (type == ItemType.SPEED_CARD || type == ItemType.STACK_CARD) {
            if (upgradeCards.size() < UPGRADE_SLOTS.length) {
                ItemStack single = stack.clone();
                single.setAmount(1);
                upgradeCards.add(single);
                onSave();
                redraw();

                if (stack.getAmount() <= 1) return null;
                ItemStack r = stack.clone();
                r.setAmount(stack.getAmount() - 1);
                return r;
            }
        }

        return stack;
    }
}
