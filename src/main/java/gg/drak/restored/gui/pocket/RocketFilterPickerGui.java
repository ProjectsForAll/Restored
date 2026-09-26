package gg.drak.restored.gui.pocket;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.AbstractInventoryGui;
import gg.drak.restored.gui.GuiItems;
import gg.drak.restored.gui.GuiUtils;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Selects a firework template from network storage without extracting it. */
public final class RocketFilterPickerGui extends AbstractInventoryGui implements PocketLinkGuiBound {
    private final Network network;
    private final Consumer<ItemStack> onPicked;
    private final UUID linkId;
    private final Map<Integer, StoredStack> entries = new HashMap<>();
    private int page;

    public RocketFilterPickerGui(Player player, Network network, UUID linkId, Consumer<ItemStack> onPicked) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.linkId = linkId;
        this.onPicked = onPicked;
    }

    @Override
    public UUID getPocketLinkId() {
        return linkId;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        PocketLinkItem.markGuiOpen(player, linkId);
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lSelect Firework Rocket");
        entries.clear();
        List<StoredStack> rockets = network.getCombinedStacks().stream()
                .filter(stack -> RocketDistributerAugmentGui.isRocket(stack.getTemplate()))
                .toList();
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int totalPages = Math.max(1, (int) Math.ceil(rockets.size() / (double) perPage));
        page = Math.min(page, totalPages - 1);
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);
        int start = page * perPage;
        int end = Math.min(start + perPage, rockets.size());
        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            StoredStack stack = rockets.get(i);
            contents[slots[slotIndex]] = GuiUtils.asGuiStack(stack);
            entries.put(slots[slotIndex], stack);
            bindSlot(slots[slotIndex], "item");
        }
        placeReturnButton(contents, "back");
        if (page > 0) {
            contents[GuiLayout.pagePrevSlot(49)] = GuiItems.pagePreviousButton(page);
            bindSlot(GuiLayout.pagePrevSlot(49), "prev");
        }
        if (page + 1 < totalPages) {
            contents[GuiLayout.pageNextSlot(49)] = GuiItems.pageNextButton(page + 2);
            bindSlot(GuiLayout.pageNextSlot(49), "next");
        }
        fillUnusedWithBlack(contents);
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
        if ("prev".equals(key)) {
            page = Math.max(0, page - 1);
            render();
        } else if ("next".equals(key)) {
            page++;
            render();
        } else if ("back".equals(key)) {
            onPicked.accept(null);
        } else if ("item".equals(key)) {
            StoredStack stored = entries.get(event.getRawSlot());
            if (stored != null) {
                ItemStack picked = stored.getTemplate().clone();
                picked.setAmount(1);
                onPicked.accept(picked);
            }
        }
    }
}
