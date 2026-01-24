package host.plas.restored;

import host.plas.bou.BetterPlugin;
import host.plas.restored.commands.GetItemCMD;
import host.plas.restored.commands.NetworkPermissionsCMD;
import host.plas.restored.config.BlockMap;
import host.plas.restored.config.MainConfig;
import host.plas.restored.config.NetworkMapConfig;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.blocks.NetworkMap;
import host.plas.restored.events.MainListener;
import host.plas.restored.timers.NetworkSaveTimer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentSkipListMap;

@Getter @Setter
public final class Restored extends BetterPlugin {
    @Getter @Setter
    private static Restored instance;
    @Getter @Setter
    private static MainConfig mainConfig;
    @Getter @Setter
    private static BlockMap blockMap;
    @Getter @Setter
    private static MainListener mainListener;
    @Getter @Setter
    private static NetworkMapConfig networkMapConfig;

    @Getter @Setter
    private static GetItemCMD getItemCMD;
    @Getter @Setter
    private static NetworkPermissionsCMD networkPermissionsCMD;

    @Getter @Setter
    private static NetworkSaveTimer networkSaveTimer;

    public Restored() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        // Plugin startup logic
        setInstance(this);

        setMainConfig(new MainConfig());
        setBlockMap(new BlockMap());
        setNetworkMapConfig(new NetworkMapConfig());

        setMainListener(new MainListener());

        setGetItemCMD(new GetItemCMD());
        setNetworkPermissionsCMD(new NetworkPermissionsCMD());

        setNetworkSaveTimer(new NetworkSaveTimer());

        NetworkMap.init();
    }

    @Override
    public void onBaseDisable() {
        // Plugin shutdown logic
        NetworkManager.getNetworks().forEach(Network::unload);

        NetworkMap.stop();
    }

    /**
     * Get a map of online players.
     * Sorted by player name.
     * @return A map of online players sorted by player name.
     */
    public ConcurrentSkipListMap<String, Player> getOnlinePlayers() {
        ConcurrentSkipListMap<String, Player> onlinePlayers = new ConcurrentSkipListMap<>();

        for (Player player : getServer().getOnlinePlayers()) {
            onlinePlayers.put(player.getName(), player);
        }

        return onlinePlayers;
    }
}
