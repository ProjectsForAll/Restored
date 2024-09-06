package host.plas.restored.utils;

import org.bukkit.Location;

public class WorldUtils {
    public static String getKeyFromLocation(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
