package host.plas.restored.events.own;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class BlockRedrawEvent extends BaseEvent {
    private ScreenBlock block;

    public BlockRedrawEvent(ScreenBlock block) {
        super();

        this.block = block;
    }

    public Location getLocation() {
        return block.getLocation();
    }
}
