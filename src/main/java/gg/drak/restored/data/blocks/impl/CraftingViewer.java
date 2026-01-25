package gg.drak.restored.data.blocks.impl;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.items.ItemUtils;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.datablock.DataBlock;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.items.impl.CraftingViewerItem;
import gg.drak.restored.data.screens.items.ViewerPage;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter @Setter
public class CraftingViewer extends NetworkBlock implements InventoryBlock {
    // Crafting grid slots: 6-8, 15-17, 24-26
    private static final int[] CRAFTING_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int CRAFTING_TABLE_SLOT = 35;
    private static final int SEARCH_SLOT = 36;
    private static final int[] BLACK_GLASS_SLOTS = {5, 14, 23, 42, 43, 44};
    
    private String currentFilter = null; // Player-specific filter
    
    public CraftingViewer(Network network, Location location) {
        super(BlockType.CRAFTING_VIEWER, network, location, CraftingViewerItem::new);
    }

    public CraftingViewer(Network network, Location location, DataBlock block) {
        super(BlockType.CRAFTING_VIEWER, network, location, CraftingViewerItem::new, block);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSave() {

    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return stack;
        }

        // Check if slot is a crafting slot - allow items there
        // For other slots, use viewer behavior
        ItemStack r;
        if (stack.getAmount() > 1) {
            r = stack.clone();
            r.setAmount(stack.getAmount() - 1);
        } else {
            r = null;
        }

        if (!canAddItem(stack)) {
            return stack;
        }

        addItem(stack);

        redraw();

        return r;
    }

    public boolean canAddItem(ItemStack stack) {
        AtomicBoolean canAdd = new AtomicBoolean(false);

        getNetwork().ifPresent(network -> {
            canAdd.set(network.canInsert(stack));
        });

        return canAdd.get();
    }

    public boolean addItem(ItemStack stack) {
        AtomicBoolean added = new AtomicBoolean(false);

        getNetwork().ifPresent(network -> {
            added.set(network.insert(stack));
        });

        return added.get();
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        Restored.getInstance().logInfo("Building inventory sheet for crafting viewer...");

        InventorySheet sheet = new InventorySheet(54);

        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return sheet;
        }
        Network network = networkOptional.get();

        // Load player filter
        loadPlayerFilter(player);

        // Get filtered page
        Optional<ViewerPage> pageOptional = getFilteredPage(network, 1);

        if (pageOptional.isEmpty()) {
            return sheet;
        }
        ViewerPage page = pageOptional.get();

        buildCraftingPage(sheet, page, player);

        return sheet;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&bCrafting Viewer");
    }

    private void buildCraftingPage(InventorySheet sheet, ViewerPage page, Player player) {
        AtomicInteger index = new AtomicInteger(0);

        // Fill items in columns 0-4 (not bottom row)
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                int slot = row * 9 + col;
                if (index.get() < page.getContents().size()) {
                    sheet.addIcon(slot, page.getContents().get(index.get()).asPageItem());
                    index.incrementAndGet();
                }
            }
        }

        // Fill black stained glass in column 5 (not bottom row)
        for (int row = 0; row < 5; row++) {
            int slot = row * 9 + 5;
            sheet.addIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        }

        // Fill black stained glass in slots 42-44
        for (int slot : BLACK_GLASS_SLOTS) {
            if (slot >= 42 && slot <= 44) {
                sheet.addIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
            }
        }

        // Crafting grid slots (6-8, 15-17, 24-26) - empty by default, can be filled by player
        // These slots are handled by InventoryBlock interface

        // Crafting table button (slot 35)
        sheet.addIcon(CRAFTING_TABLE_SLOT, getCraftingTableIcon(player));

        // Search/Filter button (slot 36)
        sheet.addIcon(SEARCH_SLOT, getSearchIcon(player));

        // Bottom row pagination
        drawBottomBar(sheet, page.getIndex());
    }

    private Icon getCraftingTableIcon(Player player) {
        ItemStack stack = ItemUtils.make(Material.CRAFTING_TABLE, "&eCrafting Table");
        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            ClickType clickType = event.getClick();
            if (clickType == ClickType.LEFT) {
                craftItem(player, 1);
            } else if (clickType == ClickType.SHIFT_LEFT) {
                craftItem(player, 64); // Craft stack
            } else if (clickType == ClickType.RIGHT) {
                clearCraftingGrid(player);
            }
            event.setCancelled(true);
        });

        return icon;
    }

    private Icon getSearchIcon(Player player) {
        ItemStack stack = ItemUtils.make(Material.NAME_TAG, "&eSearch/Filter");
        List<String> lore = new ArrayList<>();
        if (currentFilter != null && !currentFilter.isEmpty()) {
            lore.add(ColorUtils.colorizeHard("&7Current filter: &f" + currentFilter));
        } else {
            lore.add(ColorUtils.colorizeHard("&7No filter active"));
        }
        lore.add(ColorUtils.colorizeHard("&7Left click: Set filter"));
        lore.add(ColorUtils.colorizeHard("&7Right click: Clear filter"));
        lore.add(ColorUtils.colorizeHard("&7Shift left click: Use last filter"));

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            ClickType clickType = event.getClick();
            if (clickType == ClickType.LEFT) {
                // Open Anvil GUI for search input
                openSearchGUI(player);
            } else if (clickType == ClickType.RIGHT) {
                clearFilter(player);
            } else if (clickType == ClickType.SHIFT_LEFT) {
                useLastFilter(player);
            }
            event.setCancelled(true);
        });

        return icon;
    }

    private void craftItem(Player player, int amount) {
        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) {
            return;
        }
        Network network = networkOptional.get();

        // Get crafting grid items from the screen
        ScreenInstance screen = ScreenManager.getScreen(player).orElse(null);
        if (screen == null) {
            return;
        }

        // Create a crafting inventory representation
        ItemStack[] craftingGrid = new ItemStack[9];
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            int slot = CRAFTING_SLOTS[i];
            if (slot < screen.getItems().size()) {
                craftingGrid[i] = screen.getItems().get(slot).getItem();
            }
        }

        // Use Bukkit's crafting recipe system
        CraftingInventory craftingInventory = (CraftingInventory) player.getOpenInventory().getTopInventory();
        if (craftingInventory != null) {
            // Try to craft the recipe
            ItemStack result = craftingInventory.getResult();
            if (result != null && !result.getType().isAir()) {
                // Check if network has required items
                if (network.canInsert(result)) {
                    // Craft and add to network
                    for (int i = 0; i < amount; i++) {
                        ItemStack crafted = result.clone();
                        if (network.insert(crafted)) {
                            // Remove ingredients from crafting grid
                            for (ItemStack ingredient : craftingGrid) {
                                if (ingredient != null && !ingredient.getType().isAir()) {
                                    ingredient.setAmount(ingredient.getAmount() - 1);
                                    if (ingredient.getAmount() <= 0) {
                                        // Return to network or remove
                                    }
                                }
                            }
                        } else {
                            break; // Can't craft more
                        }
                    }
                    redraw();
                }
            }
        }
    }

    private void clearCraftingGrid(Player player) {
        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) {
            return;
        }
        Network network = networkOptional.get();

        ScreenInstance screen = ScreenManager.getScreen(player).orElse(null);
        if (screen == null) {
            return;
        }

        // Return all items from crafting grid to network
        for (int slot : CRAFTING_SLOTS) {
            if (slot < screen.getItems().size()) {
                ItemStack item = screen.getItems().get(slot).getItem();
                if (item != null && !item.getType().isAir()) {
                    network.insert(item);
                }
            }
        }

        redraw();
    }

    private void openSearchGUI(Player player) {
        // Open Anvil GUI for search input
        // This would require an Anvil GUI implementation
        // For now, we'll use a simple chat input or command
        Sender sender = new Sender(player);
        sender.sendMessage("&ePlease use /restored filter <text> to set a filter, or click the search icon again.");
    }

    private void clearFilter(Player player) {
        currentFilter = null;
        savePlayerFilter(player, null);
        redraw();
    }

    private void useLastFilter(Player player) {
        try {
            Optional<String> lastFilter = Restored.getDatabase().getFilterDAO().getLastFilter(player.getUniqueId().toString());
            if (lastFilter.isPresent() && !lastFilter.get().isEmpty()) {
                currentFilter = lastFilter.get();
                savePlayerFilter(player, currentFilter);
                redraw();
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get last filter for player: " + player.getName(), e);
        }
    }

    private void loadPlayerFilter(Player player) {
        try {
            Optional<String> filter = Restored.getDatabase().getFilterDAO().getLastFilter(player.getUniqueId().toString());
            currentFilter = filter.orElse(null);
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to load filter for player: " + player.getName(), e);
        }
    }

    private void savePlayerFilter(Player player, String filter) {
        try {
            if (filter == null || filter.isEmpty()) {
                Restored.getDatabase().getFilterDAO().clearFilter(player.getUniqueId().toString());
            } else {
                Restored.getDatabase().getFilterDAO().setFilter(player.getUniqueId().toString(), filter);
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to save filter for player: " + player.getName(), e);
        }
    }

    private Optional<ViewerPage> getFilteredPage(Network network, int pageIndex) {
        Optional<ViewerPage> pageOptional = network.getPage(pageIndex);
        if (pageOptional.isEmpty() || currentFilter == null || currentFilter.isEmpty()) {
            return pageOptional;
        }

        // Filter items by name
        ViewerPage page = pageOptional.get();
        List<gg.drak.restored.data.screens.items.StoredItem> filtered = new ArrayList<>();
        String filterLower = currentFilter.toLowerCase();

        for (gg.drak.restored.data.screens.items.StoredItem item : page.getContents()) {
            ItemStack stack = item.getItem();
            if (stack != null) {
                String itemName = stack.getType().name().toLowerCase();
                if (itemName.contains(filterLower)) {
                    filtered.add(item);
                } else if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                    String displayName = stack.getItemMeta().getDisplayName().toLowerCase();
                    if (displayName.contains(filterLower)) {
                        filtered.add(item);
                    }
                }
            }
        }

        return Optional.of(new ViewerPage(pageIndex, filtered));
    }

    public void setFilter(Player player, String filter) {
        currentFilter = filter;
        savePlayerFilter(player, filter);
        redraw();
    }

    public void showPage(Player player, int pageIndex, ScreenBlock block) {
        Optional<Network> networkOptional = getNetwork();
        if (!networkOptional.isPresent()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return;
        }
        Network network = networkOptional.get();

        loadPlayerFilter(player);
        Optional<ViewerPage> pageOptional = getFilteredPage(network, pageIndex);

        if (pageOptional.isEmpty()) {
            return;
        }
        ViewerPage page = pageOptional.get();

        InventorySheet sheet = new InventorySheet(54);

        buildCraftingPage(sheet, page, player);

        InventorySheet inventorySheet = buildInventorySheet(player, block);
        String title = buildTitle(player, block);

        ScreenInstance screen = new ScreenInstance(player, this.getType(), inventorySheet);
        screen.setTitle(title);
        screen.setBlock(block);

        screen.open();
    }

    public void drawBottomBar(InventorySheet sheet, int currentPage) {
        sheet.addIcon(sheet.getSize() - 8 - 1, getPageLeft(sheet, currentPage));
        sheet.addIcon(sheet.getSize() - 4 - 1, getPageMiddle(sheet, currentPage));
        sheet.addIcon(sheet.getSize() - 0 - 1, getPageRight(sheet, currentPage));
    }

    public Icon getPageMiddle(InventorySheet sheet, int page) {
        ItemStack stack = ItemUtils.make(Material.KNOWLEDGE_BOOK, "&bPage&7: &a" + page);

        return new BasicIcon(stack);
    }

    public Icon getPageLeft(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 8 - 1;

        ItemStack stack = ItemUtils.make(Material.ARROW, "&7<<< &ePrevious Page");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, page);
            container.set(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, page - 1);

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            Player player = (Player) event.getViewers().get(0);

            ScreenManager.getScreen(player).ifPresent(screen -> {
                int currentPage = -1;
                int actionPage = -1;

                ItemStack s = screen.getItems().get(offset).getItem();
                if (s == null) {
                    return;
                }
                ItemMeta m = s.getItemMeta();
                if (m != null) {
                    PersistentDataContainer c = m.getPersistentDataContainer();
                    currentPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, -1);
                    actionPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, -1);

                    if (currentPage == -1 && actionPage == -1) {
                        return;
                    }
                }

                Optional<ScreenBlock> screenBlock = screen.getScreenBlock();
                if (screenBlock.isEmpty()) return;
                ScreenBlock block = screenBlock.get();

                if (block instanceof CraftingViewer) {
                    CraftingViewer craftingViewer = (CraftingViewer) block;

                    craftingViewer.showPage(player, actionPage, craftingViewer);
                }
            });
        });

        return icon;
    }

    public Icon getPageRight(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 0 - 1;

        ItemStack stack = ItemUtils.make(Material.ARROW, "&eNext Page &7>>>");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, page);
            container.set(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, page + 1);

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            Player player = (Player) event.getViewers().get(0);

            ScreenManager.getScreen(player).ifPresent(screen -> {
                int currentPage = -1;
                int actionPage = -1;

                ItemStack s = screen.getItems().get(offset).getItem();
                if (s == null) {
                    return;
                }
                ItemMeta m = s.getItemMeta();
                if (m != null) {
                    PersistentDataContainer c = m.getPersistentDataContainer();
                    currentPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, -1);
                    actionPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, -1);

                    if (currentPage == -1 && actionPage == -1) {
                        return;
                    }
                }

                Optional<ScreenBlock> screenBlock = screen.getScreenBlock();
                if (screenBlock.isEmpty()) return;
                ScreenBlock block = screenBlock.get();

                if (block instanceof CraftingViewer) {
                    CraftingViewer craftingViewer = (CraftingViewer) block;

                    craftingViewer.showPage(player, actionPage, craftingViewer);
                }
            });
        });

        return icon;
    }
}
