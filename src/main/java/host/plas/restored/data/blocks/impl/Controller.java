package host.plas.restored.data.blocks.impl;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.ScreenBlock;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.items.impl.ControllerItem;
import host.plas.restored.data.screens.InventorySheet;
import host.plas.restored.utils.MessageUtils;
import io.streamlined.bukkit.commands.Sender;
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
public class Controller extends ScreenBlock {
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

        icon.setName(MessageUtils.colorize("&bController Heart"));
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

        lore.add(MessageUtils.colorize("&7Owner of this network&8: &c" + network.getOwner().getName()));
        lore.add(MessageUtils.colorize("&7Network ID&8: &c" + network.getIdentifier()));

        return lore;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return MessageUtils.colorize("&cDrive");
    }
}
