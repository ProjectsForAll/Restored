package gg.drak.restored.gui.compactor;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.NetworkItemsGui;
import gg.drak.restored.gui.PaginatedInventoryGui;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiConfig;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Infinite, paginated list of a network's compacting configurations. */
public final class CompactingConfigsGui extends PaginatedInventoryGui {
    private static final int NEW_SLOT = 0;
    private static final int BACK_SLOT = 49;

    private final Network network;
    private final Map<Integer, CompactConfiguration> entries = new HashMap<>();

    public CompactingConfigsGui(Player player, Network network) {
        super(GuiConfig.builder(player).cornerColor(CornerColor.YELLOW).backSlot(BACK_SLOT).build());
        this.network = network;
    }

    @Override
    public void open() {
        render();
    }

    @Override
    protected void render() {
        if (!network.hasAugment(AugmentType.COMPACTOR)) {
            new NetworkItemsGui(player, network).open();
            return;
        }
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lCompacting Configurations");
        entries.clear();
        List<CompactConfiguration> configurations = network.getCompactConfigurations();
        clampPage(configurations.size(), contents.length);
        fillCurrentPage(contents, configurations, contents.length, (arr, slot, configuration) -> {
            arr[slot] = CompactingConfigIcon.create(configuration);
            entries.put(slot, configuration);
            bindSlot(slot, "config:" + configuration.getIdentifier());
        });

        contents[NEW_SLOT] = GuiItems.button(Material.NETHER_STAR, "#00FC88&lNew Configuration",
                List.of("#bdc8c9Click to create a new CompactConfiguration."));
        bindSlot(NEW_SLOT, "new");
        placeReturnButton(contents, "back");
        placePaginationChrome(contents, configurations.size());
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (handlePaginationClick(key)) {
            return;
        }
        if ("back".equals(key)) {
            new gg.drak.restored.gui.augments.AugmentsListGui(player, network).open();
            return;
        }
        if (!network.canUseAugments(player.getUniqueId())) {
            player.sendMessage(LegacyColors.color("#FF5555You cannot modify augments on this network."));
            return;
        }
        if ("new".equals(key)) {
            CompactConfiguration configuration = network.createCompactConfiguration();
            network.save();
            new CompactingConfigEditGui(player, network, configuration.getIdentifier()).open();
            return;
        }
        if (key != null && key.startsWith("config:")) {
            try {
                UUID id = UUID.fromString(key.substring("config:".length()));
                if (network.getCompactConfiguration(id) != null) {
                    new CompactingConfigEditGui(player, network, id).open();
                }
            } catch (IllegalArgumentException ignored) {
                // Stale GUI key.
            }
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // All configuration icons are display-only clones; there is no item to return.
    }
}
