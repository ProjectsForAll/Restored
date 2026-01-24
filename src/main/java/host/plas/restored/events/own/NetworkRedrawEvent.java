package host.plas.restored.events.own;

import host.plas.bou.gui.screens.events.BlockRedrawEvent;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NetworkRedrawEvent extends BlockRedrawEvent {
    private Network network;

    public NetworkRedrawEvent(Network network, NetworkBlock block) {
        super(block);

        this.network = network;
    }
}
