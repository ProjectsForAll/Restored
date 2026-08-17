package gg.drak.restored.gui.augments.workstations;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.WorkstationSession;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.augments.AbstractWorkstationGui;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingWorkstationGui extends AbstractWorkstationGui {
    private static final int ITEM = 0;
    private static final int LAPIS = 1;
    private static final int ITEM_INV = 19;
    private static final int LAPIS_INV = 21;
    private static final int WORK_INV = 25;
    private static final int[] OFFER_SLOTS = {29, 31, 33};

    private final List<Offer> offers = new ArrayList<>();
    private long lastSeed = -1;
    private int bookshelfCount;

    public EnchantingWorkstationGui(Player player, Network network) {
        super(player, network, AugmentType.ENCHANTING);
        bookshelfCount = Math.max(
                network.getEnchantingBookshelves(),
                network.getOrCreateWorkstationSession(AugmentType.ENCHANTING).getEnchantingBookshelves()
        );
    }

    @Override
    protected Iterable<Integer> craftSlotIds() {
        return List.of(ITEM, LAPIS);
    }

    @Override
    protected int outputToggleInvSlot() {
        return 41;
    }

    @Override
    protected int outputBufferInvSlot() {
        return 42;
    }

    @Override
    protected void renderContents(ItemStack[] contents) {
        contents[7] = GuiItems.button(
                Material.BOOKSHELF,
                "#FFED6A&lBookshelves: " + bookshelfCount + "/15",
                List.of(
                        "#bdc8c9Add up to 15 bookshelves to raise offer levels.",
                        "#bdc8c9Shift-click bookshelves from your inventory.",
                        "#bdc8c9Click with an empty cursor to remove them."
                )
        );
        bindSlot(7, "bookshelves");
        contents[ITEM_INV] = displaySlot(ITEM, "Item to Enchant");
        bindCraftSlot(ITEM_INV, ITEM);
        contents[LAPIS_INV] = displaySlot(LAPIS, "Lapis Lazuli");
        bindCraftSlot(LAPIS_INV, LAPIS);
        contents[WORK_INV] = workstationButton(List.of(
                "#bdc8c9Click an offer below to enchant.",
                "#bdc8c9Right-click workstation to clear slots."
        ));
        bindWorkstation(WORK_INV);

        refreshOffers();
        for (int i = 0; i < 3; i++) {
            int slot = OFFER_SLOTS[i];
            if (i < offers.size()) {
                Offer offer = offers.get(i);
                contents[slot] = GuiItems.button(
                        Material.ENCHANTED_BOOK,
                        "#FFED6A&lOffer " + (i + 1),
                        List.of(
                                "#bdc8c9" + pretty(offer.enchantment()) + " " + offer.level(),
                                "#bdc8c9Cost: #FFED6A" + offer.cost() + " levels",
                                "#bdc8c9Lapis: #FFED6A" + (i + 1),
                                "#00FC88Click to enchant"
                        )
                );
                bindSlot(slot, "offer:" + i);
            } else {
                contents[slot] = GuiItems.button(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "#AAAAAANo Offer",
                        List.of("#bdc8c9Place an enchantable item and lapis.")
                );
            }
        }
    }

    private void refreshOffers() {
        ItemStack item = craftSlots.get(ITEM);
        ItemStack lapis = craftSlots.get(LAPIS);
        offers.clear();
        if (item == null || lapis == null || lapis.getType() != Material.LAPIS_LAZULI) {
            return;
        }
        long seed = item.getType().ordinal() * 31L + item.getAmount() + player.getLevel();
        if (seed != lastSeed || offers.isEmpty()) {
            lastSeed = seed;
        }
        Random random = new Random(seed);
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : Enchantment.values()) {
            if (enchantment.isCursed()) {
                continue;
            }
            if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK
                    || enchantment.canEnchantItem(item)) {
                candidates.add(enchantment);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int playerLevel = Math.max(1, player.getLevel());
        for (int i = 0; i < 3 && !candidates.isEmpty(); i++) {
            Enchantment enchantment = candidates.get(random.nextInt(candidates.size()));
            int max = Math.max(1, enchantment.getMaxLevel());
            int offeredMax = Math.max(1, Math.min(max,
                    1 + (int) Math.ceil(bookshelfCount * (max - 1) / 15.0)));
            int level = 1 + random.nextInt(offeredMax);
            int cost = Math.min(playerLevel, (i + 1) * Math.max(1, playerLevel / 3 + level));
            cost = Math.max(i + 1, Math.min(30, cost));
            offers.add(new Offer(enchantment, level, cost));
        }
    }

    @Override
    protected void handleExtraClick(String key, InventoryClickEvent event) {
        if ("bookshelves".equals(key)) {
            handleBookshelfClick(event);
            return;
        }
        if (!key.startsWith("offer:")) {
            return;
        }
        int index = Integer.parseInt(key.substring("offer:".length()));
        if (index < 0 || index >= offers.size()) {
            return;
        }
        Offer offer = offers.get(index);
        ItemStack item = craftSlots.get(ITEM);
        ItemStack lapis = craftSlots.get(LAPIS);
        if (item == null || lapis == null || lapis.getType() != Material.LAPIS_LAZULI) {
            player.sendMessage(LegacyColors.color("#FF5555Need an item and lapis."));
            return;
        }
        int lapisCost = index + 1;
        if (lapis.getAmount() < lapisCost) {
            player.sendMessage(LegacyColors.color("#FF5555Need " + lapisCost + " lapis."));
            return;
        }
        if (player.getLevel() < offer.cost()) {
            player.sendMessage(LegacyColors.color("#FF5555Need " + offer.cost() + " levels."));
            return;
        }

        ItemStack result = item.clone();
        result.setAmount(1);
        if (result.getType() == Material.BOOK) {
            result.setType(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) result.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(offer.enchantment(), offer.level(), true);
                result.setItemMeta(meta);
            }
        } else {
            ItemMeta meta = result.getItemMeta();
            if (meta == null) {
                return;
            }
            meta.addEnchant(offer.enchantment(), offer.level(), true);
            result.setItemMeta(meta);
        }
        if (!canAcceptResult(result)) {
            return;
        }

        player.setLevel(player.getLevel() - offer.cost());
        if (item.getAmount() <= 1) {
            craftSlots.remove(ITEM);
        } else {
            item.setAmount(item.getAmount() - 1);
            craftSlots.put(ITEM, item);
        }
        if (lapis.getAmount() <= lapisCost) {
            craftSlots.remove(LAPIS);
        } else {
            lapis.setAmount(lapis.getAmount() - lapisCost);
            craftSlots.put(LAPIS, lapis);
        }
        depositResult(result);
        lastSeed = ThreadLocalRandom.current().nextLong();
        player.sendMessage(LegacyColors.color("#00FC88Enchanted with " + pretty(offer.enchantment()) + " " + offer.level() + "."));
        render();
    }

    private void handleBookshelfClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();
        if (hasCursor && cursor.getType() != Material.BOOKSHELF) {
            player.sendMessage(LegacyColors.color("#FF5555Only bookshelves can go here."));
            return;
        }
        if (!hasCursor) {
            if (bookshelfCount <= 0) {
                return;
            }
            int amount = event.isRightClick() ? 1 : bookshelfCount;
            event.getView().setCursor(new ItemStack(Material.BOOKSHELF, amount));
            bookshelfCount -= amount;
            saveBookshelves();
            render();
            return;
        }
        int space = 15 - bookshelfCount;
        if (space <= 0) {
            player.sendMessage(LegacyColors.color("#FF5555This enchantment augment already has 15 bookshelves."));
            return;
        }
        int move = event.isRightClick() ? 1 : Math.min(space, cursor.getAmount());
        bookshelfCount += move;
        cursor.setAmount(cursor.getAmount() - move);
        if (cursor.getAmount() <= 0) {
            event.getView().setCursor(null);
        }
        saveBookshelves();
        render();
    }

    @Override
    protected int handleSpecialShiftClick(ItemStack fromPlayer) {
        if (fromPlayer == null || fromPlayer.getType() != Material.BOOKSHELF || bookshelfCount >= 15) {
            return 0;
        }
        int move = Math.min(15 - bookshelfCount, fromPlayer.getAmount());
        bookshelfCount += move;
        fromPlayer.setAmount(fromPlayer.getAmount() - move);
        saveBookshelves();
        return move;
    }

    @Override
    protected void saveExtraSession(WorkstationSession session) {
        session.setEnchantingBookshelves(bookshelfCount);
        network.setEnchantingBookshelves(bookshelfCount);
    }

    private void saveBookshelves() {
        network.setEnchantingBookshelves(bookshelfCount);
        network.getOrCreateWorkstationSession(AugmentType.ENCHANTING)
                .setEnchantingBookshelves(bookshelfCount);
    }

    @Override
    protected void onWorkstationClick(ClickType clickType) {
        // Offers are clicked directly; workstation button is informational for left-click
        player.sendMessage(LegacyColors.color("#bdc8c9Click one of the enchantment offers below."));
    }

    private static String pretty(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    @Override
    protected void openPicker(int slotId) {
        lastSeed = -1;
        super.openPicker(slotId);
    }

    private record Offer(Enchantment enchantment, int level, int cost) {
    }
}
