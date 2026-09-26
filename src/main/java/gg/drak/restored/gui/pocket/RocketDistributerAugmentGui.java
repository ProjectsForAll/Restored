package gg.drak.restored.gui.pocket;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIResult;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Configuration screen for the Pocket Link's network-backed rocket reserve. */
public final class RocketDistributerAugmentGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private static final int FILTER_ROW = 1;
    private static final int MODE_SLOT = 29;
    private static final int META_SLOT = 31;
    private static final int ENABLE_SLOT = 33;
    private static final int AMOUNT_SLOT = 40;
    private static final ConcurrentHashMap<UUID, UUID> PENDING_AMOUNTS = new ConcurrentHashMap<>();
    private static AmountChatListener amountChatListener;

    private final UUID linkId;

    public RocketDistributerAugmentGui(Player player, UUID linkId) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(49).build());
        this.linkId = linkId;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    private ItemStack linkItem() {
        return PocketLinkItem.findInInventory(player, linkId);
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack link = linkItem();
        if (link == null) {
            player.sendMessage(LegacyColors.color("#FF5555Pocket Link not found."));
            return;
        }
        PocketLinkItem.markGuiOpen(player, linkId);
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lRocket Distributer");

        List<ItemStack> filters = PocketLinkItem.getRocketFilters(link);
        for (int col = 1; col <= PocketLinkItem.FILTER_SLOTS; col++) {
            int index = col - 1;
            int slot = FILTER_ROW * 9 + col;
            ItemStack filter = filters.get(index);
            contents[slot] = filter == null || filter.getType().isAir()
                    ? GuiItems.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "#AAAAAARocket Filter " + (index + 1),
                    List.of("#bdc8c9Click to select a firework from the network.",
                            "#bdc8c9Or place a firework from your cursor."))
                    : withHint(filter, List.of("#bdc8c9Click to remove or replace this filter."));
            bindSlot(slot, "filter:" + index);
        }

        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getRocketFilterMode(link);
        contents[MODE_SLOT] = GuiItems.button(Material.PAPER, "#FFED6A&lFilter Mode", List.of(
                "#bdc8c9Click to toggle whitelist/blacklist.", "",
                modeLine(mode == PocketLinkItem.FeedFilterMode.WHITELIST, "Whitelist"),
                modeLine(mode == PocketLinkItem.FeedFilterMode.BLACKLIST, "Blacklist (default)")));
        bindSlot(MODE_SLOT, "mode");

        PocketLinkItem.FeedMetaMode meta = PocketLinkItem.getRocketMetaMode(link);
        contents[META_SLOT] = GuiItems.button(Material.COMMAND_BLOCK, "#FFED6A&lRocket Meta Data", List.of(
                "#bdc8c9Click to toggle rocket matching.", "",
                modeLine(meta == PocketLinkItem.FeedMetaMode.RESPECT, "Respect Rocket Meta Data"),
                modeLine(meta == PocketLinkItem.FeedMetaMode.ANY, "Allow Any Rocket Meta Data")));
        bindSlot(META_SLOT, "meta");

        boolean enabled = PocketLinkItem.isRocketEnabled(link);
        contents[ENABLE_SLOT] = GuiItems.button(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                "#FFED6A&lRocket Distributer: " + (enabled ? "Enabled" : "Disabled"),
                List.of("#bdc8c9Click to toggle this pocket augment.",
                        enabled ? "#00FC88The reserve will be replenished." : "#FF5555No rockets will be added."));
        bindSlot(ENABLE_SLOT, "enabled");

        long keep = PocketLinkItem.getRocketKeepAmount(link);
        contents[AMOUNT_SLOT] = GuiItems.button(Material.FIREWORK_ROCKET,
                "#FFED6A&lKeep Rockets: " + keep,
                List.of("#bdc8c9Click to enter an exact amount.",
                        "#AAAAAAThe amount is shared across prioritized rocket slots.",
                        "#AAAAAAOff hand, main hand, then inventory slot id."));
        bindSlot(AMOUNT_SLOT, "amount");

        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    private static String modeLine(boolean selected, String label) {
        return (selected ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private static ItemStack withHint(ItemStack stack, List<String> hints) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            hints.forEach(line -> lore.add(LegacyColors.color(line)));
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }
        ItemStack link = linkItem();
        if (link == null) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory().equals(player.getInventory())) {
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (isRocket(clicked) && placeIntoFilterSlots(clicked) > 0) {
                if (clicked.getAmount() <= 0) {
                    event.setCurrentItem(null);
                }
                render();
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
        switch (key) {
            case "back" -> new PocketAugmentsGui(player, linkId).open();
            case "mode" -> {
                PocketLinkItem.setRocketFilterMode(link, PocketLinkItem.getRocketFilterMode(link).toggle());
                render();
            }
            case "meta" -> {
                PocketLinkItem.setRocketMetaMode(link, PocketLinkItem.getRocketMetaMode(link).toggle());
                render();
            }
            case "enabled" -> {
                PocketLinkItem.setRocketEnabled(link, !PocketLinkItem.isRocketEnabled(link));
                render();
            }
            case "amount" -> openAmountPrompt();
            default -> {
                if (key.startsWith("filter:")) {
                    handleFilterClick(link, Integer.parseInt(key.substring("filter:".length())), event);
                }
            }
        }
    }

    private void handleFilterClick(ItemStack link, int index, InventoryClickEvent event) {
        List<ItemStack> filters = PocketLinkItem.getRocketFilters(link);
        ItemStack existing = filters.get(index);
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (!isRocket(cursor)) {
                player.sendMessage(LegacyColors.color("#FF5555Rocket filters only accept firework rockets."));
                return;
            }
            filters.set(index, cursor.clone());
            event.getView().setCursor(existing == null ? null : existing.clone());
            PocketLinkItem.setRocketFilters(link, filters);
            render();
            return;
        }
        if (existing != null && !existing.getType().isAir()) {
            filters.set(index, null);
            PocketLinkItem.setRocketFilters(link, filters);
            event.getView().setCursor(existing.clone());
            render();
            return;
        }
        openFilterPicker(index);
    }

    private int placeIntoFilterSlots(ItemStack fromPlayer) {
        List<ItemStack> filters = PocketLinkItem.getRocketFilters(linkItem());
        int moved = 0;
        for (int i = 0; i < filters.size() && fromPlayer.getAmount() > 0; i++) {
            ItemStack existing = filters.get(i);
            if (existing == null || !existing.isSimilar(fromPlayer)) {
                continue;
            }
            int move = Math.min(existing.getMaxStackSize() - existing.getAmount(), fromPlayer.getAmount());
            if (move > 0) {
                existing.setAmount(existing.getAmount() + move);
                fromPlayer.setAmount(fromPlayer.getAmount() - move);
                moved += move;
            }
        }
        for (int i = 0; i < filters.size() && fromPlayer.getAmount() > 0; i++) {
            if (filters.get(i) == null || filters.get(i).getType().isAir()) {
                filters.set(i, fromPlayer.clone());
                moved += fromPlayer.getAmount();
                fromPlayer.setAmount(0);
            }
        }
        if (moved > 0) {
            PocketLinkItem.setRocketFilters(linkItem(), filters);
        }
        return moved;
    }

    private void openFilterPicker(int index) {
        ItemStack link = linkItem();
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if (networkId.isEmpty()) {
            player.sendMessage(LegacyColors.color("#FF5555Link a network first."));
            return;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null) {
            player.sendMessage(LegacyColors.color("#FF5555Linked network no longer exists."));
            return;
        }
        new RocketFilterPickerGui(player, network, linkId, picked -> {
            ItemStack current = linkItem();
            if (current != null && picked != null) {
                List<ItemStack> next = PocketLinkItem.getRocketFilters(current);
                next.set(index, picked);
                PocketLinkItem.setRocketFilters(current, next);
            }
            new RocketDistributerAugmentGui(player, linkId).open();
        }).open();
    }

    private void openAmountPrompt() {
        player.closeInventory();
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^", "Enter amount", "")
                    .callHandlerSynchronously(Restored.getInstance())
                    .setHandler((p, result) -> {
                        String input = firstInput(result);
                        Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                            ItemStack link = linkItem();
                            if (link != null) {
                                try {
                                    PocketLinkItem.setRocketKeepAmount(link, Long.parseLong(input));
                                } catch (NumberFormatException e) {
                                    p.sendMessage(LegacyColors.color("#FF5555Enter a whole number from 0 to 2368."));
                                }
                                new RocketDistributerAugmentGui(p, linkId).open();
                            }
                        });
                        return List.of();
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            player.sendMessage(LegacyColors.color("#FFED6AEnter the exact rocket amount in chat, or type cancel."));
            ensureAmountChatListener();
            PENDING_AMOUNTS.put(player.getUniqueId(), linkId);
        }
    }

    private static String firstInput(SignGUIResult result) {
        for (String line : result.getLinesWithoutColor()) {
            if (line != null && !line.isBlank() && !line.contains("^") && !line.equalsIgnoreCase("Enter amount")) {
                return line.trim();
            }
        }
        return "";
    }

    public static boolean isRocket(ItemStack stack) {
        return stack != null && stack.getType() == Material.FIREWORK_ROCKET;
    }

    private static void ensureAmountChatListener() {
        if (amountChatListener != null) {
            return;
        }
        amountChatListener = new AmountChatListener();
        Restored.getInstance().registerListener(amountChatListener);
    }

    private static final class AmountChatListener implements org.bukkit.event.Listener {

        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
        public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
            UUID linkId = PENDING_AMOUNTS.remove(event.getPlayer().getUniqueId());
            if (linkId == null) {
                return;
            }
            event.setCancelled(true);
            String message = event.getMessage().trim();
            Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                if (!message.equalsIgnoreCase("cancel")) {
                    ItemStack link = PocketLinkItem.findInInventory(event.getPlayer(), linkId);
                    try {
                        if (link != null) {
                            PocketLinkItem.setRocketKeepAmount(link, Long.parseLong(message));
                        }
                    } catch (NumberFormatException ignored) {
                        event.getPlayer().sendMessage(LegacyColors.color("#FF5555Enter a whole number from 0 to 2368."));
                    }
                }
                new RocketDistributerAugmentGui(event.getPlayer(), linkId).open();
            });
        }
    }
}
