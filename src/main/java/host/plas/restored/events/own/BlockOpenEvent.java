package host.plas.restored.events.own;

import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import tv.quaint.events.components.BaseEvent;

@Getter @Setter
public class BlockOpenEvent extends BaseEvent {
    private Player player;
    private ScreenBlock block;

    public BlockOpenEvent(Player player, ScreenBlock block) {
        super();

        this.player = player;
        this.block = block;
    }

    public Location getLocation() {
        return block.getLocation();
    }
}
