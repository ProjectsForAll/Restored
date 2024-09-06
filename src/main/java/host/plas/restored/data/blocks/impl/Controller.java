package host.plas.restored.data.blocks.impl;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.utils.ColorUtils;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.items.impl.ControllerItem;
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

    public Controller(Network network, Location location, DataBlock block) {
        super(BlockType.CONTROLLER, network, location, ControllerItem::new, block);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSave() {

    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(getType().getSlots());

        sheet.addIcon(4, getMainIcon(player, block));

        return sheet;
    }

    public Icon getMainIcon(Player player, ScreenBlock block) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);

        Icon icon = new Icon(stack);

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
        return ColorUtils.colorizeHard("&cDrive");
    }
}
