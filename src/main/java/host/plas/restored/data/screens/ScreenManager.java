package host.plas.restored.data.screens;

import host.plas.restored.data.blocks.ScreenBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ScreenManager {

    @Getter @Setter
    private static ConcurrentHashMap<Player, ScreenInstance> screens = new ConcurrentHashMap<>();

    public static Optional<ScreenInstance> getScreen(Player player) {
        AtomicReference<Optional<ScreenInstance>> screen = new AtomicReference<>(Optional.empty());

        screens.forEach((p, s) -> {
            if (p.getUniqueId().equals(player.getUniqueId())) {
                screen.set(Optional.of(s));
            }
        });

        return screen.get();
    }

    public static void setScreen(Player player, ScreenInstance screen) {
        if (hasScreen(player)) {
            removeScreen(player);
        }

        screens.put(player, screen);
    }

    public static void removeScreen(Player player) {
        screens.forEach((p, sheet) -> {
            if (p.getUniqueId().equals(player.getUniqueId())) {
                screens.remove(p);
            }
        });
    }

    public static boolean hasScreen(Player player) {
        return getScreen(player).isPresent();
    }

    public static ConcurrentHashMap<Player, ScreenInstance> getPlayersOf(ScreenBlock block) {
        ConcurrentHashMap<Player, ScreenInstance> players = new ConcurrentHashMap<>();

        getScreens().forEach((p, s) -> {
            if (s.getBlock().equals(block)) {
                players.put(p, s);
            }
        });

        return players;
    }
}
