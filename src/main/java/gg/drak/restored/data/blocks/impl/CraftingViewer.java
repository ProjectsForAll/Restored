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
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.items.impl.CraftingViewerItem;
import gg.drak.restored.data.screens.items.StoredItem;
import gg.drak.restored.data.screens.items.ViewerPage;
import gg.drak.restored.gui.NetworkGuiScreenInstance;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter @Setter
public class CraftingViewer extends NetworkBlock implements InventoryBlock {
    // Crafting grid slots: 6-8, 15-17, 24-26
    private static final int[] CRAFTING_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int CRAFTING_TABLE_SLOT = 35;
    private static final int SEARCH_SLOT = 36;
    private static final int[] BLACK_GLASS_SLOTS = {5, 14, 23, 42, 43, 44};
    private static final int ITEMS_PER_PAGE = 25; // 5 columns * 5 rows

    // Per-player filters (player UUID -> filter string)
    private final ConcurrentHashMap<UUID, String> playerFilters = new ConcurrentHashMap<>();

    public CraftingViewer(Network network, Location location) {
        super(BlockType.CRAFTING_VIEWER, network, location, CraftingViewerItem::new);
    }

    public CraftingViewer(java.util.UUID uuid, Network network, Location location, com.google.gson.JsonObject data) {
        super(BlockType.CRAFTING_VIEWER, uuid, network, location, CraftingViewerItem::new, data);
    }

    @Override
    protected ScreenInstance createScreenInstance(Player player, InventorySheet inventorySheet) {
        return new NetworkGuiScreenInstance(player, getType(), inventorySheet, CraftingViewer::isCraftingSlot);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSaveSpecific() {

    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return stack;
        }

        Optional<Network> networkOpt = getNetwork();
        if (networkOpt.isEmpty()) return stack;
        Network network = networkOpt.get();

        if (! network.canInsert(stack)) {
            return stack;
        }

        int originalAmount = stack.getAmount();
        int leftover = network.insertItems(stack);

        if (leftover < originalAmount) {
            redraw();
        }

        if (leftover <= 0) {
            return null;
        }

        ItemStack r = stack.clone();
        r.setAmount(leftover);
        return r;
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
        String filter = loadPlayerFilter(player);

        // Get filtered page
        Optional<ViewerPage> pageOptional = getFilteredPage(network, 1, filter);

        if (pageOptional.isEmpty()) {
            // Still build the crafting UI even with no items
            buildCraftingPage(sheet, new ViewerPage(1, new ArrayList<>()), player);
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
                    sheet.setIcon(slot, page.getContents().get(index.get()).asPageItem());
                    index.incrementAndGet();
                }
            }
        }

        // Fill black stained glass in column 5 (not bottom row)
        for (int row = 0; row < 5; row++) {
            int slot = row * 9 + 5;
            sheet.setIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        }

        // Fill black stained glass in slots 42-44
        for (int slot : BLACK_GLASS_SLOTS) {
            if (slot >= 42 && slot <= 44) {
                sheet.setIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
            }
        }

        // Crafting grid slots (6-8, 15-17, 24-26) - empty by default, can be filled by player

        // Crafting table button (slot 35)
        sheet.setIcon(CRAFTING_TABLE_SLOT, getCraftingTableIcon(player));

        // Search/Filter button (slot 36)
        String currentFilter = playerFilters.get(player.getUniqueId());
        sheet.setIcon(SEARCH_SLOT, getSearchIcon(player, currentFilter));

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

    private Icon getSearchIcon(Player player, String currentFilter) {
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
        if (networkOptional.isEmpty()) return;

        // Read crafting grid from the actual open inventory
        org.bukkit.inventory.Inventory inv = player.getOpenInventory().getTopInventory();

        ItemStack[] craftingGrid = new ItemStack[9];
        boolean hasIngredient = false;
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
            craftingGrid[i] = item != null ? item.clone() : null;
            if (item != null && !item.getType().isAir()) hasIngredient = true;
        }

        if (!hasIngredient) return;

        // Use Bukkit recipe matching API
        Recipe recipe = Bukkit.getCraftingRecipe(craftingGrid, player.getWorld());
        if (recipe == null) {
            new Sender(player).sendMessage("&cNo recipe matches the crafting grid.");
            return;
        }

        ItemStack result = recipe.getResult();

        for (int crafted = 0; crafted < amount; crafted++) {
            // Verify ingredients are still present
            boolean canCraft = true;
            for (int j = 0; j < CRAFTING_SLOTS.length; j++) {
                ItemStack gridItem = inv.getItem(CRAFTING_SLOTS[j]);
                ItemStack needed = craftingGrid[j];
                if (needed != null && !needed.getType().isAir()) {
                    if (gridItem == null || gridItem.getType().isAir() || gridItem.getAmount() < 1) {
                        canCraft = false;
                        break;
                    }
                }
            }

            if (!canCraft) break;

            // Give result to player
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(result.clone());
            if (!leftover.isEmpty()) {
                // Player inventory full
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }

            // Remove one of each ingredient from crafting grid
            for (int j = 0; j < CRAFTING_SLOTS.length; j++) {
                ItemStack gridItem = inv.getItem(CRAFTING_SLOTS[j]);
                if (gridItem != null && !gridItem.getType().isAir()) {
                    if (gridItem.getAmount() > 1) {
                        gridItem.setAmount(gridItem.getAmount() - 1);
                    } else {
                        inv.setItem(CRAFTING_SLOTS[j], null);
                    }
                }
            }

            // Re-check recipe still matches after consuming ingredients (for craft-stack)
            if (crafted < amount - 1) {
                ItemStack[] newGrid = new ItemStack[9];
                boolean stillHasIngredient = false;
                for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
                    ItemStack item = inv.getItem(CRAFTING_SLOTS[i]);
                    newGrid[i] = item != null ? item.clone() : null;
                    if (item != null && !item.getType().isAir()) stillHasIngredient = true;
                }
                if (!stillHasIngredient) break;

                Recipe nextRecipe = Bukkit.getCraftingRecipe(newGrid, player.getWorld());
                if (nextRecipe == null || !nextRecipe.getResult().isSimilar(result)) break;
            }
        }

        redraw();
    }

    private void clearCraftingGrid(Player player) {
        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) return;
        Network network = networkOptional.get();

        org.bukkit.inventory.Inventory inv = player.getOpenInventory().getTopInventory();

        // Return all items from crafting grid to network
        for (int slot : CRAFTING_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                network.insert(item);
                inv.setItem(slot, null);
            }
        }

        redraw();
    }

    private void openSearchGUI(Player player) {
        Sender sender = new Sender(player);
        sender.sendMessage("&ePlease use /restored filter <text> to set a filter, or click the search icon again.");
    }

    private void clearFilter(Player player) {
        playerFilters.remove(player.getUniqueId());
        savePlayerFilter(player, null);
        redraw();
    }

    private void useLastFilter(Player player) {
        try {
            Optional<String> lastFilter = Restored.getDatabase().getFilterDAO().getLastFilter(player.getUniqueId().toString());
            if (lastFilter.isPresent() && !lastFilter.get().isEmpty()) {
                playerFilters.put(player.getUniqueId(), lastFilter.get());
                savePlayerFilter(player, lastFilter.get());
                redraw();
            }
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to get last filter for player: " + player.getName(), e);
        }
    }

    /**
     * Load the player's filter from the database.
     * @return the filter string, or null if no filter.
     */
    private String loadPlayerFilter(Player player) {
        try {
            Optional<String> filter = Restored.getDatabase().getFilterDAO().getLastFilter(player.getUniqueId().toString());
            String filterStr = filter.orElse(null);
            if (filterStr != null) {
                playerFilters.put(player.getUniqueId(), filterStr);
            } else {
                playerFilters.remove(player.getUniqueId());
            }
            return filterStr;
        } catch (Exception e) {
            Restored.getInstance().logSevere("Failed to load filter for player: " + player.getName(), e);
            return null;
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

    private Optional<ViewerPage> getFilteredPage(Network network, int pageIndex, String filter) {
        if (filter == null || filter.isEmpty()) {
            return network.getPage(pageIndex, ITEMS_PER_PAGE);
        }

        // Get ALL items and filter, then paginate
        Optional<ViewerPage> allOptional = network.getPage(1, Integer.MAX_VALUE);
        if (allOptional.isEmpty()) return Optional.empty();

        String filterLower = filter.toLowerCase();
        List<StoredItem> filtered = new ArrayList<>();

        for (StoredItem item : allOptional.get().getContents()) {
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

        if (filtered.isEmpty()) return Optional.empty();

        int startIndex = (pageIndex - 1) * ITEMS_PER_PAGE;
        if (startIndex >= filtered.size()) return Optional.empty();
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filtered.size());

        return Optional.of(new ViewerPage(pageIndex, filtered.subList(startIndex, endIndex)));
    }

    public void setFilter(Player player, String filter) {
        if (filter == null || filter.isEmpty()) {
            playerFilters.remove(player.getUniqueId());
        } else {
            playerFilters.put(player.getUniqueId(), filter);
        }
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

        String filter = loadPlayerFilter(player);
        Optional<ViewerPage> pageOptional = getFilteredPage(network, pageIndex, filter);

        if (pageOptional.isEmpty()) {
            return;
        }
        ViewerPage page = pageOptional.get();

        InventorySheet inventorySheet = new InventorySheet(54);
        buildCraftingPage(inventorySheet, page, player);

        String title = buildTitle(player, block);

        ScreenInstance screen = createScreenInstance(player, inventorySheet);
        screen.setTitle(title);
        screen.setBlock(block);

        screen.open();
    }

    public void drawBottomBar(InventorySheet sheet, int currentPage) {
        sheet.setIcon(sheet.getSize() - 8 - 1, getPageLeft(sheet, currentPage));
        sheet.setIcon(sheet.getSize() - 4 - 1, getPageMiddle(sheet, currentPage));
        sheet.setIcon(sheet.getSize() - 0 - 1, getPageRight(sheet, currentPage));
    }

    /**
     * Check if a raw slot index is a crafting grid slot.
     */
    public static boolean isCraftingSlot(int slot) {
        for (int s : CRAFTING_SLOTS) {
            if (s == slot) return true;
        }
        return false;
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
