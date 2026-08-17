package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class ConfirmGui extends AbstractInventoryGui {
    private final String title;
    private final Consumer<Player> onConfirm;
    private final Runnable onCancel;

    public ConfirmGui(Player player, String title, Consumer<Player> onConfirm, Runnable onCancel) {
        super(player, CornerColor.YELLOW);
        this.title = title;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void open() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, title);
        contents[11] = GuiItems.cancelButton();
        contents[15] = GuiItems.confirmButton();
        bindSlot(11, "cancel");
        bindSlot(15, "confirm");
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if ("confirm".equals(key)) {
            player.closeInventory();
            onConfirm.accept(player);
        } else if ("cancel".equals(key)) {
            player.closeInventory();
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }
}
