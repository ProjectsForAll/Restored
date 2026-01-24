package gg.drak.restored.events.own;

import host.plas.bou.gui.screens.events.BlockCloseEvent;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.NetworkBlock;
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
