package host.plas.restored.events.own;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

@Getter @Setter
public class NetworkOpenEvent extends BlockOpenEvent {
    private Network network;

    public NetworkOpenEvent(Network network, Player player, NetworkBlock block) {
        super(player, block);

        this.network = network;
    }
}
