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
import gg.drak.restored.data.items.impl.CrafterItem;
import gg.drak.restored.data.screens.items.StoredItem;
import gg.drak.restored.gui.NetworkGuiScreenInstance;
import gg.drak.restored.serialization.PersistedItemCodec;
import host.plas.bou.gui.screens.events.BlockCloseEvent;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.items.ItemData;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.items.ItemUtils;
import host.plas.bou.utils.ColorUtils;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.math.BigInteger;
import java.util.*;

/**
 * Auto-crafts items using a configured recipe pattern, pulling ingredients from the network.
 * GUI layout (54 slots):
 * Row 0-2 (slots 0-26):
 *   [0-4]   Result display & status (col 0-4)
 *   [5]     Separator
 *   [6-8]   Recipe row 1 (col 6-8)
 * Row 1:
 *   [9-13]  Upgrade card slots (col 0-4)
 *   [14]    Separator
 *   [15-17] Recipe row 2
 * Row 2:
 *   [18-22] Empty
 *   [23]    Separator
 *   [24-26] Recipe row 3
 * Row 3-4: Separator / info
 * Row 5: Bottom bar
 */
@Getter @Setter
public class Crafter extends NetworkBlock implements Tickable, UpgradeHolder, InventoryBlock {
    private static final int BASE_TICK_RATE = 40; // 2 seconds
    private static final int[] RECIPE_SLOTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private static final int[] UPGRADE_SLOTS = {9, 10, 11, 12};
    private static final int TOGGLE_SLOT = 13;
    private static final int RESULT_SLOT = 4;
    private static final int[] SEPARATOR_SLOTS = {5, 14, 23};

    private int tickRate = BASE_TICK_RATE;
    private int tickCounter = 0;
    private List<ItemStack> upgradeCards = new ArrayList<>();
    private ItemStack[] recipePattern = new ItemStack[9];
    private boolean enabled = true;

    public Crafter(Network network, Location location) {
        super(BlockType.CRAFTER, network, location, CrafterItem::new);
        onLoad();
    }

    public Crafter(UUID uuid, Network network, Location location, JsonObject data) {
        super(BlockType.CRAFTER, uuid, network, location, CrafterItem::new, data);
        onLoad();
    }

    @Override
    public void onLoad() {
        upgradeCards = new ArrayList<>();
        recipePattern = new ItemStack[9];
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

        if (data.has("recipe")) {
            JsonArray arr = data.getAsJsonArray("recipe");
            for (int i = 0; i < Math.min(arr.size(), 9); i++) {
                String itemStr = arr.get(i).getAsString();
                if (!itemStr.isEmpty()) {
                    recipePattern[i] = PersistedItemCodec.deserializePayload(itemStr);
                    if (recipePattern[i] != null && recipePattern[i].getType() == Material.BARRIER) {
                        recipePattern[i] = null;
                    }
                }
            }
        }

        if (data.has("enabled")) {
            enabled = data.get("enabled").getAsBoolean();
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

        JsonArray recipeArr = new JsonArray();
        for (ItemStack slot : recipePattern) {
            if (slot != null && !slot.getType().isAir()) {
                recipeArr.add(new ItemData(UUID.randomUUID().toString(), BigInteger.ONE, slot).getData());
            } else {
                recipeArr.add("");
            }
        }
        getData().add("recipe", recipeArr);
        getData().addProperty("enabled", enabled);
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
        if (!enabled) return;

        syncFromOpenInventory();

        Optional<Network> networkOpt = getNetwork();
        if (networkOpt.isEmpty()) return;
        Network network = networkOpt.get();

        // Check if we have a valid recipe
        boolean hasRecipe = false;
        for (ItemStack slot : recipePattern) {
            if (slot != null && !slot.getType().isAir()) {
                hasRecipe = true;
                break;
            }
        }
        if (!hasRecipe) return;

        // Match recipe
        Recipe recipe = Bukkit.getCraftingRecipe(recipePattern, getBlock().getWorld());
        if (recipe == null) return;

        ItemStack result = recipe.getResult();
        int maxCrafts = getTransferAmount();

        // Build ingredient requirement map (using isSimilar for grouping)
        List<ItemStack> uniqueIngredients = new ArrayList<>();
        List<Integer> requiredPerCraft = new ArrayList<>();

        for (ItemStack ingredient : recipePattern) {
            if (ingredient == null || ingredient.getType().isAir()) continue;

            boolean found = false;
            for (int i = 0; i < uniqueIngredients.size(); i++) {
                if (uniqueIngredients.get(i).isSimilar(ingredient)) {
                    requiredPerCraft.set(i, requiredPerCraft.get(i) + 1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                uniqueIngredients.add(StoredItem.flattenStack(ingredient));
                requiredPerCraft.add(1);
            }
        }

        // Limit crafts by ingredient availability
        for (int i = 0; i < uniqueIngredients.size(); i++) {
            BigInteger available = BigInteger.ZERO;
            for (StorageDisk disk : network.getDisks()) {
                available = available.add(disk.getQuantity(uniqueIngredients.get(i)));
            }
            int possible = available.divide(BigInteger.valueOf(requiredPerCraft.get(i))).intValue();
            maxCrafts = Math.min(maxCrafts, possible);
        }

        if (maxCrafts <= 0) return;

        ItemStack batchResult = result.clone();
        batchResult.setAmount(result.getAmount() * maxCrafts);

        if (!network.canFullyInsert(batchResult)) return;

        // Remove ingredients from network
        for (int i = 0; i < uniqueIngredients.size(); i++) {
            ItemStack ingredient = uniqueIngredients.get(i);
            BigInteger toRemove = BigInteger.valueOf((long) requiredPerCraft.get(i) * maxCrafts);

            for (StorageDisk disk : network.getDisks()) {
                if (toRemove.compareTo(BigInteger.ZERO) <= 0) break;
                Optional<StoredItem> stored = disk.getStoredItem(ingredient);
                if (stored.isEmpty()) continue;

                BigInteger available = stored.get().getAmount();
                BigInteger remove = toRemove.min(available);
                disk.removeItem(stored.get(), remove);
                disk.save();
                toRemove = toRemove.subtract(remove);
            }
        }

        network.insertItems(batchResult);
    }

    @Override
    protected ScreenInstance createScreenInstance(Player player, InventorySheet inventorySheet) {
        return new NetworkGuiScreenInstance(player, getType(), inventorySheet, slot -> {
            for (int s : RECIPE_SLOTS) if (s == slot) return true;
            for (int s : UPGRADE_SLOTS) if (s == slot) return true;
            return false;
        });
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(54);

        // Result display
        Recipe recipe = Bukkit.getCraftingRecipe(recipePattern, getBlock().getWorld());
        if (recipe != null) {
            ItemStack resultDisplay = recipe.getResult().clone();
            sheet.setIcon(RESULT_SLOT, new Icon(resultDisplay));
        } else {
            sheet.setIcon(RESULT_SLOT, new Icon(ItemUtils.make(Material.BARRIER, "&cNo valid recipe")));
        }

        // Status info
        ItemStack statusItem = ItemUtils.make(Material.REDSTONE_TORCH,
                enabled ? "&aEnabled" : "&cDisabled",
                "&7Click to toggle");
        Icon toggleIcon = new Icon(statusItem);
        toggleIcon.onClick(event -> {
            enabled = !enabled;
            onSave();
            redraw();
        });
        sheet.setIcon(TOGGLE_SLOT, toggleIcon);

        // Separators
        for (int slot : SEPARATOR_SLOTS) {
            sheet.setIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
        }

        // Recipe slots
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            if (recipePattern[i] != null && !recipePattern[i].getType().isAir()) {
                sheet.setIcon(RECIPE_SLOTS[i], new BasicIcon(recipePattern[i]));
            } else {
                sheet.setIcon(RECIPE_SLOTS[i], new BasicIcon(Material.AIR));
            }
        }

        // Upgrade card slots
        for (int i = 0; i < UPGRADE_SLOTS.length; i++) {
            if (i < upgradeCards.size() && upgradeCards.get(i) != null) {
                sheet.setIcon(UPGRADE_SLOTS[i], new BasicIcon(upgradeCards.get(i)));
            } else {
                sheet.setIcon(UPGRADE_SLOTS[i], new BasicIcon(Material.AIR));
            }
        }

        // Build sets of used slots to fill the rest with glass
        Set<Integer> usedSlots = new HashSet<>();
        usedSlots.add(RESULT_SLOT);
        usedSlots.add(TOGGLE_SLOT);
        for (int s : SEPARATOR_SLOTS) usedSlots.add(s);
        for (int s : RECIPE_SLOTS) usedSlots.add(s);
        for (int s : UPGRADE_SLOTS) usedSlots.add(s);

        for (int slot = 0; slot < 54; slot++) {
            if (!usedSlots.contains(slot)) {
                sheet.setIcon(slot, new Icon(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)));
            }
        }

        return sheet;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&dCrafter");
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
        }

        return stack;
    }

    @Override
    public void onClose(BlockCloseEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.Inventory inv = player.getOpenInventory().getTopInventory();
        readRecipeFromInventory(inv);

        // Return recipe items to network (they are just pattern placeholders — keep them in the crafter)
        // Upgrade cards placed manually also stay
    }

    /**
     * Called when the GUI closes — read recipe items from the actual inventory.
     */
    public void readRecipeFromInventory(org.bukkit.inventory.Inventory inv) {
        readRecipeFromInventory(inv, true);
    }

    private void readRecipeFromInventory(org.bukkit.inventory.Inventory inv, boolean persist) {
        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            ItemStack item = inv.getItem(RECIPE_SLOTS[i]);
            if (item != null && !item.getType().isAir()) {
                recipePattern[i] = StoredItem.flattenStack(item);
            } else {
                recipePattern[i] = null;
            }
        }
        if (persist) {
            onSave();
        }
    }

    private void syncFromOpenInventory() {
        host.plas.bou.gui.ScreenManager.getPlayersOf(this).forEach(screen -> {
            org.bukkit.inventory.Inventory inv = screen.getPlayer().getOpenInventory().getTopInventory();
            readRecipeFromInventory(inv, false);

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
