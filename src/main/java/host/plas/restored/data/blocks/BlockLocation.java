package host.plas.restored.data.blocks;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

@Getter @Setter
public class BlockLocation implements Comparable<BlockLocation> {
    private String world;
    private int x, y, z;

    public BlockLocation(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int compareTo(@NotNull BlockLocation o) {
        if (this.world.equals(o.world)) {
            if (this.x == o.x) {
                if (this.y == o.y) {
                    return Integer.compare(this.z, o.z);
                } else {
                    return Integer.compare(this.y, o.y);
                }
            } else {
                return Integer.compare(this.x, o.x);
            }
        } else {
            return this.world.compareTo(o.world);
        }
    }

    public boolean equals(BlockLocation location) {
        return this.world.equals(location.getWorld()) && this.x == location.getX() && this.y == location.getY() && this.z == location.getZ();
    }

    public String asString() {
        return "!!" + world + ";" + x + ";" + y + ";" + z + ";";
    }

    public Block toBlock() {
        return new Location(Bukkit.getWorld(world), x, y, z).getBlock();
    }

    public Location toLocation() {
        return toBlock().getLocation();
    }

    public static BlockLocation of(String string) {
        if (string.startsWith("!!")) {
            string = string.substring(2);
        }
        String[] split = string.split(";");
        return new BlockLocation(split[0], Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
    }

    public static BlockLocation of(Block block) {
        return new BlockLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockLocation of(Location location) {
        return of(location.getBlock());
    }
}
