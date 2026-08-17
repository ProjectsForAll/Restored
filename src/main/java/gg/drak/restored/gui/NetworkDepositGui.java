package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Plain 6-row chest used as a bulk deposit buffer. On close, contents are inserted into the network.
 */
public class NetworkDepositGui extends AbstractInventoryGui {
    private final Network network;
    private boolean deposited;

    public NetworkDepositGui(Player player, Network network) {
        super(player, CornerColor.YELLOW);
        this.network = network;
    }

    @Override
    public void open() {
        deposited = false;
        slotKeys.clear();
        inventory = Bukkit.createInventory(this, GuiLayout.SIZE_LARGE, LegacyColors.color("#FFED6A&lDeposit Items"));
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Allow normal chest interaction.
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Allow normal chest interaction.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (deposited) {
            return;
        }
        deposited = true;
        depositAll();

        Bukkit.getScheduler().runTask(Restored.getInstance(), () -> {
            if (player.isOnline()) {
                new NetworkItemsGui(player, network).open();
            }
        });
    }

    private void depositAll() {
        if (inventory == null) {
            return;
        }

        ItemStack[] contents = inventory.getContents();
        inventory.clear();

        if (!network.canDeposit(player.getUniqueId())) {
            returnAll(contents);
            player.sendMessage(LegacyColors.color("#FF5555You cannot deposit into this network."));
            return;
        }

        long insertedTotal = 0;
        long returnedTotal = 0;

        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            long amount = stack.getAmount();
            long inserted = network.insert(stack, amount);
            insertedTotal += inserted;
            long left = amount - inserted;
            if (left > 0) {
                ItemStack leftover = stack.clone();
                leftover.setAmount((int) left);
                returnedTotal += left;
                giveOrDrop(leftover);
            }
        }

        if (insertedTotal > 0) {
            network.save();
            player.sendMessage(LegacyColors.color("#00FC88Deposited #FFED6A" + insertedTotal + " #00FC88item(s) into the network."));
        }
        if (returnedTotal > 0) {
            player.sendMessage(LegacyColors.color("#FF5555Network full — returned #FFED6A" + returnedTotal + " #FF5555item(s)."));
        }
    }

    private void returnAll(ItemStack[] contents) {
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            giveOrDrop(stack.clone());
        }
    }

    private void giveOrDrop(ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
