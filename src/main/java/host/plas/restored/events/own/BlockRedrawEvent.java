package host.plas.restored.events.own;

import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class BlockRedrawEvent extends BaseEvent {
    private NetworkBlock block;

    public BlockRedrawEvent(NetworkBlock block) {
        super();

        this.block = block;
    }

    public Location getLocation() {
        return block.getLocation();
    }
}
