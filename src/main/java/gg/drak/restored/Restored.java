package gg.drak.restored;

import gg.drak.restored.config.DatabaseConfig;
import gg.drak.restored.database.MainOperator;
import host.plas.bou.BetterPlugin;
import gg.drak.restored.commands.GetItemCMD;
import gg.drak.restored.commands.NetworkPermissionsCMD;
import gg.drak.restored.config.MainConfig;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.blocks.NetworkMap;
import gg.drak.restored.events.MainListener;
import gg.drak.restored.timers.NetworkSaveTimer;
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
    private static MainListener mainListener;

    @Getter @Setter
    private static GetItemCMD getItemCMD;
    @Getter @Setter
    private static NetworkPermissionsCMD networkPermissionsCMD;

    @Getter @Setter
    private static NetworkSaveTimer networkSaveTimer;

    @Getter @Setter
    private static DatabaseConfig databaseConfig;
    @Getter @Setter
    private static MainOperator database;

    public Restored() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        // Plugin startup logic
        setInstance(this);

        setMainConfig(new MainConfig());
        setDatabaseConfig(new DatabaseConfig());
        setDatabase(new MainOperator());
        getDatabase().ensureDatabase();

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
