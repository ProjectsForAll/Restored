package gg.drak.restored.data.blocks.impl;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.items.impl.ControllerItem;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter @Setter
public class Controller extends NetworkBlock {
    public Controller(Network network, Location location) {
        super(BlockType.CONTROLLER, network, location, ControllerItem::new);
    }

    public Controller(java.util.UUID uuid, Network network, Location location, com.google.gson.JsonObject data) {
        super(BlockType.CONTROLLER, uuid, network, location, ControllerItem::new, data);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSaveSpecific() {

    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        Restored.getInstance().logInfo("Building inventory sheet for controller...");

        InventorySheet sheet = new InventorySheet(getType().getSlots());

        // Single hopper row — center slot
        sheet.setIcon(4, getMainIcon(player, block));

        return sheet;
    }

    public Icon getMainIcon(Player player, ScreenBlock block) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);

        Icon icon = new BasicIcon(stack);

        icon.setName(ColorUtils.colorizeHard("&bController Heart"));
        icon.setLore(getMainIconLore(player, block));

        return icon;
    }

    public List<String> getMainIconLore(Player player, ScreenBlock block) {
        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return new ArrayList<>();
        }
        Network network = networkOptional.get();

        List<String> lore = new ArrayList<>();

        lore.add(ColorUtils.colorizeHard("&7Owner of this network&8: &c" + network.getOwner().getName()));
        lore.add(ColorUtils.colorizeHard("&7Network ID&8: &c" + network.getIdentifier()));

        return lore;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&aNetwork Controller");
    }
}
