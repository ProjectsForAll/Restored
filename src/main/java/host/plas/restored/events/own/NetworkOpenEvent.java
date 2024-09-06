package host.plas.restored.events.own;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class NetworkOpenEvent extends BlockOpenEvent {
    private Network network;

    public NetworkOpenEvent(Network network, Player player, ScreenBlock block) {
        super(player, block);

        this.network = network;
    }
}
