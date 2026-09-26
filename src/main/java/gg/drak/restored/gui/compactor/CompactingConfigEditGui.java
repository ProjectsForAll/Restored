package gg.drak.restored.gui.compactor;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIResult;
import gg.drak.restored.Restored;
import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.data.CompactingAction;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.QuantityOperand;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.ConfirmGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.NetworkHopperFilterPickerGui;
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
import java.util.UUID;

/** Editor for one CompactConfiguration. */
public final class CompactingConfigEditGui extends AbstractInventoryGui {
    private static final int ITEM_SLOT = 20;
    private static final int ENABLE_SLOT = 29;
    private static final int ACTION_SLOT = 31;
    private static final int OPERAND_SLOT = 33;
    private static final int QUANTITY_SLOT = 40;
    private static final int DELETE_SLOT = 42;
    private static final int BACK_SLOT = 49;

    private final Network network;
    private final UUID configurationId;

    public CompactingConfigEditGui(Player player, Network network, UUID configurationId) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
        this.configurationId = configurationId;
    }

    private CompactConfiguration configuration() {
        return network.getCompactConfiguration(configurationId);
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        CompactConfiguration configuration = configuration();
        if (configuration == null) {
            new CompactingConfigsGui(player, network).open();
            return;
        }
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lEdit Compacting Configuration");

        ItemStack item = configuration.getItem();
        contents[ITEM_SLOT] = item == null
                ? GuiItems.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "#AAAAAAItem Filter",
                List.of("#bdc8c9Click to select an item from the network.",
                        "#bdc8c9Or place an item on your cursor."))
                : withHint(item, List.of("#bdc8c9Click to remove or replace this item."));
        bindSlot(ITEM_SLOT, "item");

        contents[ENABLE_SLOT] = GuiItems.button(
                configuration.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "#FFED6A&lConfiguration: " + (configuration.isEnabled() ? "Enabled" : "Disabled"),
                List.of("#bdc8c9Click to toggle this configuration."));
        bindSlot(ENABLE_SLOT, "enabled");

        contents[ACTION_SLOT] = GuiItems.button(
                configuration.getAction() == CompactingAction.COMPACT ? Material.IRON_BLOCK : Material.IRON_INGOT,
                "#FFED6A&lAction: " + configuration.getAction().name(),
                List.of("#bdc8c9Click to switch between COMPACT and DECOMPACT.",
                        "#AAAAAACOMPACT: ingots into blocks.",
                        "#AAAAAADECOMPACT: blocks into ingots."));
        bindSlot(ACTION_SLOT, "action");

        contents[OPERAND_SLOT] = GuiItems.button(Material.COMPARATOR,
                "#FFED6A&lQuantity Operand",
                List.of("#bdc8c9Click to cycle the comparison.",
                        "#00FC88" + configuration.getOperand().display()));
        bindSlot(OPERAND_SLOT, "operand");

        contents[QUANTITY_SLOT] = GuiItems.button(Material.HOPPER,
                "#FFED6A&lQuantity: " + configuration.getQuantity(),
                List.of("#bdc8c9Click to enter an exact amount.",
                        "#AAAAAACondition: " + configuration.getOperand().display()
                                + " " + configuration.getQuantity()));
        bindSlot(QUANTITY_SLOT, "quantity");

        contents[DELETE_SLOT] = GuiItems.button(Material.BARRIER, "#FF5555&lDelete Configuration",
                List.of("#bdc8c9Click to permanently remove this rule."));
        bindSlot(DELETE_SLOT, "delete");
        placeReturnButton(contents, "back");
        fillUnusedWithBlack(contents);
        finishAndOpen(contents);
    }

    private static ItemStack withHint(ItemStack stack, List<String> hints) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
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
        CompactConfiguration configuration = configuration();
        if (configuration == null) {
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
            if (clicked != null && !clicked.getType().isAir()) {
                configuration.setItem(clicked);
                saveAndRender();
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
            case "back" -> new CompactingConfigsGui(player, network).open();
            case "enabled" -> {
                configuration.setEnabled(!configuration.isEnabled());
                saveAndRender();
            }
            case "action" -> {
                configuration.setAction(configuration.getAction().toggle());
                saveAndRender();
            }
            case "operand" -> {
                configuration.setOperand(configuration.getOperand().next());
                saveAndRender();
            }
            case "quantity" -> openQuantityPrompt();
            case "delete" -> new ConfirmGui(player, "Delete Compacting Configuration?", p -> {
                if (network.canUseAugments(p.getUniqueId()) && network.removeCompactConfiguration(configurationId)) {
                    network.save();
                    new CompactingConfigsGui(p, network).open();
                }
            }, () -> new CompactingConfigEditGui(player, network, configurationId).open()).open();
            case "item" -> handleItemClick(configuration, event);
            default -> {
            }
        }
    }

    private void handleItemClick(CompactConfiguration configuration, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack existing = configuration.getItem();
        if (cursor != null && !cursor.getType().isAir()) {
            configuration.setItem(cursor);
            event.getView().setCursor(existing == null ? null : existing.clone());
            saveAndRender();
            return;
        }
        if (existing != null) {
            configuration.clearItem();
            event.getView().setCursor(existing.clone());
            saveAndRender();
            return;
        }
        openItemPicker();
    }

    private void openItemPicker() {
        new NetworkHopperFilterPickerGui(player, network, picked -> {
            CompactConfiguration current = configuration();
            if (current != null && picked != null) {
                current.setItem(picked);
                network.markDirty();
                network.save();
            }
            new CompactingConfigEditGui(player, network, configurationId).open();
        }, "#FFED6A&lSelect Compactor Item").open();
    }

    private void saveAndRender() {
        network.markDirty();
        network.save();
        render();
    }

    private void openQuantityPrompt() {
        player.closeInventory();
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^", "Enter quantity", "")
                    .callHandlerSynchronously(Restored.getInstance())
                    .setHandler((p, result) -> {
                        String input = firstInput(result);
                        Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
                            CompactConfiguration current = configuration();
                            if (current != null) {
                                try {
                                    long quantity = Long.parseLong(input);
                                    if (quantity < 0) {
                                        throw new NumberFormatException();
                                    }
                                    current.setQuantity(quantity);
                                    network.markDirty();
                                    network.save();
                                } catch (NumberFormatException e) {
                                    p.sendMessage(LegacyColors.color("#FF5555Enter a whole number of 0 or greater."));
                                }
                                new CompactingConfigEditGui(p, network, configurationId).open();
                            }
                        });
                        return List.of();
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            player.sendMessage(LegacyColors.color("#FF5555The quantity input is unavailable right now."));
            new CompactingConfigEditGui(player, network, configurationId).open();
        }
    }

    private static String firstInput(SignGUIResult result) {
        for (String line : result.getLinesWithoutColor()) {
            if (line != null && !line.isBlank() && !line.contains("^") && !line.equalsIgnoreCase("Enter quantity")) {
                return line.trim();
            }
        }
        return "";
    }
}
