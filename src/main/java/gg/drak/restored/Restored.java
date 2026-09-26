package gg.drak.restored;

import gg.drak.restored.commands.NetworksCMD;
import gg.drak.restored.commands.RGetItemCMD;
import gg.drak.restored.commands.RInfoCMD;
import gg.drak.restored.commands.RRecipesCMD;
import gg.drak.restored.config.DatabaseConfig;
import gg.drak.restored.config.MainConfig;
import gg.drak.restored.config.RecipesConfig;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.PlayerPreferences;
import gg.drak.restored.database.MainOperator;
import gg.drak.restored.events.ChestLinkingToolListener;
import gg.drak.restored.events.CompactorAugmentListener;
import gg.drak.restored.events.CraftGuardListener;
import gg.drak.restored.events.FeedingAugmentListener;
import gg.drak.restored.events.ItemLoreGuardListener;
import gg.drak.restored.events.MainListener;
import gg.drak.restored.events.NetworkHopperListener;
import gg.drak.restored.events.MagnetPocketAugmentListener;
import gg.drak.restored.events.PocketLinkListener;
import gg.drak.restored.events.QuiverAugmentListener;
import gg.drak.restored.events.RocketDistributerAugmentListener;
import gg.drak.restored.gui.GuiListener;
import gg.drak.restored.gui.NetworkBrowserPrefs;
import gg.drak.restored.items.RestoredItemRegistry;
import gg.drak.restored.recipes.RecipeRegistrar;
import gg.drak.restored.timers.NetworkSaveTimer;
import host.plas.bou.BetterPlugin;
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
    private static CraftGuardListener craftGuardListener;
    @Getter @Setter
    private static ItemLoreGuardListener itemLoreGuardListener;
    @Getter @Setter
    private static GuiListener guiListener;
    @Getter @Setter
    private static PocketLinkListener pocketLinkListener;
    @Getter @Setter
    private static ChestLinkingToolListener chestLinkingToolListener;
    @Getter @Setter
    private static NetworkHopperListener networkHopperListener;
    @Getter @Setter
    private static MagnetPocketAugmentListener magnetPocketAugmentListener;
    @Getter @Setter
    private static FeedingAugmentListener feedingAugmentListener;
    @Getter @Setter
    private static QuiverAugmentListener quiverAugmentListener;
    @Getter @Setter
    private static RocketDistributerAugmentListener rocketDistributerAugmentListener;
    @Getter @Setter
    private static CompactorAugmentListener compactorAugmentListener;
    @Getter @Setter
    private static NetworksCMD networksCMD;
    @Getter @Setter
    private static RRecipesCMD rRecipesCMD;
    @Getter @Setter
    private static RInfoCMD rInfoCMD;
    @Getter @Setter
    private static RGetItemCMD rGetItemCMD;
    @Getter @Setter
    private static NetworkSaveTimer networkSaveTimer;
    @Getter @Setter
    private static DatabaseConfig databaseConfig;
    @Getter @Setter
    private static RecipesConfig recipesConfig;
    @Getter @Setter
    private static MainOperator database;

    public Restored() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        setInstance(this);

        setMainConfig(new MainConfig());
        setDatabaseConfig(new DatabaseConfig());
        setRecipesConfig(new RecipesConfig());
        NetworkBrowserPrefs.init();
        setDatabase(new MainOperator());
        getDatabase().ensureDatabase();
        getDatabase().ensureTables();
        PlayerPreferences.init(getDatabase());

        NetworkManager.loadAll(getDatabase().loadAllNetworks());

        setMainListener(new MainListener());
        setPocketLinkListener(new PocketLinkListener());
        setChestLinkingToolListener(new ChestLinkingToolListener());
        setNetworkHopperListener(new NetworkHopperListener());
        setMagnetPocketAugmentListener(new MagnetPocketAugmentListener());
        setFeedingAugmentListener(new FeedingAugmentListener());
        setQuiverAugmentListener(new QuiverAugmentListener());
        setRocketDistributerAugmentListener(new RocketDistributerAugmentListener());
        setCompactorAugmentListener(new CompactorAugmentListener());
        setCraftGuardListener(new CraftGuardListener());
        setItemLoreGuardListener(new ItemLoreGuardListener());
        setGuiListener(new GuiListener());
        setNetworksCMD(new NetworksCMD());
        setRRecipesCMD(new RRecipesCMD());
        setRInfoCMD(new RInfoCMD());
        setRGetItemCMD(new RGetItemCMD());
        RestoredItemRegistry.registerWithItemFactory();
        RecipeRegistrar.register();
        setNetworkSaveTimer(new NetworkSaveTimer());
    }

    @Override
    public void onBaseDisable() {
        RestoredItemRegistry.unregisterFromItemFactory();
        // Snapshot dirty networks on the shutdown thread, then block until DB writes finish.
        NetworkManager.saveAllDirty();
        getDatabase().getMiddleware().flush();
    }

    public ConcurrentSkipListMap<String, Player> getOnlinePlayers() {
        ConcurrentSkipListMap<String, Player> onlinePlayers = new ConcurrentSkipListMap<>();
        for (Player player : getServer().getOnlinePlayers()) {
            onlinePlayers.put(player.getName(), player);
        }
        return onlinePlayers;
    }
}
