package host.plas.restored.data.screens;

import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.pagination.PaginationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

@Getter
@Setter
    public class ScreenInstance extends Gui {
    private final PaginationManager pagination = new PaginationManager(this);

    private String partialIdentifier;
    private ScreenBlock block;
    private InventorySheet inventorySheet;

    public ScreenInstance(Player player, String identifier, String title, InventorySheet inventorySheet, ScreenBlock block) {
        super(player, "screen." + identifier, title, inventorySheet.getRows());

        this.partialIdentifier = identifier;
        this.block = block;
        this.inventorySheet = inventorySheet;
    }

    public void updateSize(int size) {
        super.setSize(size);
    }

    public void updateTitle(String title) {
        super.setTitle(title);
    }

    public void build(InventorySheet sheet) {
        sheet.getSlots().forEach(s -> {
            addItem(s.getIndex(), s.getIcon());
        });
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        super.onClose(event);

        this.getBlock().onClose((Player) event.getPlayer());
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        build(inventorySheet);

        ScreenManager.setScreen(player, this);
    }

    public void close() {
        player.closeInventory();
    }
}