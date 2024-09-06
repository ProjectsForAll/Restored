package host.plas.restored.events.own;

import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class BlockCloseEvent extends BaseEvent {
    private NetworkBlock block;
    private Player player;

    public BlockCloseEvent(Player player, NetworkBlock block) {
        super();

        this.block = block;
        this.player = player;
    }

    public Location getLocation() {
        return block.getLocation();
    }
}
