package host.plas.restored.events.own;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class NetworkRedrawEvent extends BlockRedrawEvent {
    private Network network;

    public NetworkRedrawEvent(Network network, ScreenBlock block) {
        super(block);

        this.network = network;
    }
}
