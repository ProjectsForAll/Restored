package host.plas.restored.events.own;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.NetworkBlock;
import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tv.quaint.events.components.BaseEvent;

import java.util.Optional;

@Getter @Setter
public class NetworkCloseEvent extends BlockCloseEvent {
    private Network network;

    public NetworkCloseEvent(Network network, Player player, ScreenBlock block) {
        super(player, block);
    }
}
