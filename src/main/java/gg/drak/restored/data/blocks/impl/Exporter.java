package gg.drak.restored.data.blocks.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.Tickable;
import gg.drak.restored.data.blocks.UpgradeHolder;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.ItemType;
import gg.drak.restored.data.items.impl.ExporterItem;
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

/**
 * Pushes items from the network into an adjacent container.
 * GUI layout (9 slots):
 * [0-3] Filter items (which items to export — required, won't export without filter)
 * [4]   Separator
 * [5-8] Upgrade card slots
 */
@Getter @Setter
public class Exporter extends NetworkBlock implements Tickable, UpgradeHolder, InventoryBlock {
    private static final int BASE_TICK_RATE = 20; // 1 second
    private static final int[] FILTER_SLOTS = {0, 1, 2, 3};
    private static final int[] UPGRADE_SLOTS = {5, 6, 7, 8};

    private int tickRate = BASE_TICK_RATE;
    private int tickCounter = 0;
    private List<ItemStack> filterItems = new ArrayList<>();
    private List<ItemStack> upgradeCards = new ArrayList<>();

    public Exporter(Network network, Location location) {
        super(BlockType.EXPORTER, network, location, ExporterItem::new);
        onLoad();
    }

    public Exporter(UUID uuid, Network network, Location location, JsonObject data) {
        super(BlockType.EXPORTER, uuid, network, location, ExporterItem::new, data);
        onLoad();
    }

    @Override
    public void onLoad() {
        filterItems = new ArrayList<>();
        upgradeCards = new ArrayList<>();
        JsonObject data = getData();
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
        if (data.has("filters")) {
            JsonArray arr = data.getAsJsonArray("filters");
            for (int i = 0; i < arr.size(); i++) {
                String itemStr = arr.get(i).getAsString();
                if (!itemStr.isEmpty()) {
                    ItemStack item = gg.drak.restored.serialization.PersistedItemCodec.deserializePayload(itemStr);
                    if (item != null && item.getType() != Material.BARRIER) {
                        filterItems.add(gg.drak.restored.data.screens.items.StoredItem.flattenStack(item));
                    }
                }
            }
        }
    }

    @Override
    public void onSaveSpecific() {
        JsonArray upgradesArr = new JsonArray();
        for (ItemStack card : upgradeCards) {
            if (card != null) {
                ItemType type = ItemManager.getTypeFrom(card);
                if (type == ItemType.SPEED_CARD || type == ItemType.STACK_CARD) {
                    upgradesArr.add(type.name());
                }
            }
        }
        getData().add("upgrades", upgradesArr);

        JsonArray filtersArr = new JsonArray();
        for (ItemStack filter : filterItems) {
            if (filter != null && !filter.getType().isAir()) {
                filtersArr.add(new host.plas.bou.gui.items.ItemData(
                        java.util.UUID.randomUUID().toString(),
                        java.math.BigInteger.ONE, filter).getData());
            }
        }
        getData().add("filters", filtersArr);
    }

    @Override
    public int getTickRate() {
        return getEffectiveTickRate(BASE_TICK_RATE);
    }

    @Override
    public void setTickRate(int tickRate) {
        this.tickRate = tickRate;
    }

    @Override
    public void onTick() {
        syncFromOpenInventory();

        if (filterItems.isEmpty()) return; // Exporter requires filter items

        Optional<Network> networkOpt = getNetwork();
        if (networkOpt.isEmpty()) return;
        Network network = networkOpt.get();

        Optional<Container> containerOpt = getAdjacentContainer();
        if (containerOpt.isEmpty()) return;

        Inventory inv = containerOpt.get().getInventory();
        int transferAmount = getTransferAmount();

        for (ItemStack filterItem : filterItems) {
            if (filterItem == null || filterItem.getType().isAir()) continue;

            // Find this item in the network's disks
            for (StorageDisk disk : network.getDisks()) {
                Optional<StoredItem> storedOpt = disk.getStoredItem(filterItem);
                if (storedOpt.isEmpty()) continue;
                StoredItem stored = storedOpt.get();

                if (stored.getAmount().compareTo(BigInteger.ZERO) <= 0) continue;

                int toExport = Math.min(transferAmount, stored.getAmount().min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue());

                ItemStack exportStack = stored.getItem().clone();
                exportStack.setAmount(toExport);

                HashMap<Integer, ItemStack> leftover = inv.addItem(exportStack);
                int inserted = toExport;
                for (ItemStack left : leftover.values()) {
                    inserted -= left.getAmount();
                }

                if (inserted > 0) {
                    network.removeItem(stored, BigInteger.valueOf(inserted));
                    return; // One operation per tick
                }
            }
        }
    }

    public Optional<Container> getAdjacentContainer() {
        Block self = getBlock();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        for (BlockFace face : faces) {
            Block relative = self.getRelative(face);
            if (relative.getState() instanceof Container) {
                return Optional.of((Container) relative.getState());
            }
        }
        return Optional.empty();
    }

    @Override
    protected ScreenInstance createScreenInstance(Player player, InventorySheet inventorySheet) {
        return new NetworkGuiScreenInstance(player, getType(), inventorySheet, slot -> {
            for (int s : FILTER_SLOTS) if (s == slot) return true;
            for (int s : UPGRADE_SLOTS) if (s == slot) return true;
            return false;
        });
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(9);

        // Filter slots (0-3)
        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            if (i < filterItems.size() && filterItems.get(i) != null) {
                sheet.setIcon(FILTER_SLOTS[i], new BasicIcon(filterItems.get(i)));
            } else {
                sheet.setIcon(FILTER_SLOTS[i], new BasicIcon(Material.AIR));
            }
        }

        // Separator
        sheet.setIcon(4, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));

        // Upgrade card slots (5-8)
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
        return ColorUtils.colorizeHard("&6Exporter");
    }

    public void onClose(BlockCloseEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.Inventory inv = player.getOpenInventory().getTopInventory();

        // Read filter items from inventory
        filterItems.clear();
        for (int slot : FILTER_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                filterItems.add(gg.drak.restored.data.screens.items.StoredItem.flattenStack(item));
            }
        }

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

        ItemType type = ItemManager.getTypeFrom(stack);

        // Upgrade cards go to upgrade slots
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
            return stack;
        }

        // Regular items go to filter slots
        if (filterItems.size() < FILTER_SLOTS.length) {
            ItemStack single = stack.clone();
            single.setAmount(1);
            filterItems.add(single);
            onSave();
            redraw();
            if (stack.getAmount() <= 1) return null;
            ItemStack r = stack.clone();
            r.setAmount(stack.getAmount() - 1);
            return r;
        }

        return stack;
    }

    private void syncFromOpenInventory() {
        host.plas.bou.gui.ScreenManager.getPlayersOf(this).forEach(screen -> {
            org.bukkit.inventory.Inventory inv = screen.getPlayer().getOpenInventory().getTopInventory();

            filterItems.clear();
            for (int slot : FILTER_SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    filterItems.add(StoredItem.flattenStack(item));
                }
            }

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
        });
    }
}
