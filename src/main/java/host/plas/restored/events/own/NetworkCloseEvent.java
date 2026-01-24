package host.plas.restored.events.own;

import host.plas.bou.gui.screens.events.BlockCloseEvent;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter @Setter
public class NetworkCloseEvent extends BlockCloseEvent {
    private Network network;

    public NetworkCloseEvent(Network network, Player player, NetworkBlock block) {
        super(player, block);
    }
}
