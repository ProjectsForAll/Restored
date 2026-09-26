package gg.drak.restored.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import gg.drak.restored.Restored;

public class MainConfig extends SimpleConfiguration {
    public static final String LINKED_CHEST_MAX_DISTANCE_PATH = "linked-chest-max-distance";
    public static final int DEFAULT_LINKED_CHEST_MAX_DISTANCE = 64;

    public static final String MAGNET_INTERVAL_PATH = "tick-intervals.magnet";
    public static final int DEFAULT_MAGNET_INTERVAL = 5;
    public static final String HOPPER_INTERVAL_PATH = "tick-intervals.hoppers";
    public static final int DEFAULT_HOPPER_INTERVAL = 10;
    public static final String COMPACTOR_INTERVAL_PATH = "tick-intervals.compactor";
    public static final int DEFAULT_COMPACTOR_INTERVAL = 40;
    public static final String ROCKET_INTERVAL_PATH = "tick-intervals.rocket-distributer";
    public static final int DEFAULT_ROCKET_INTERVAL = 40;

    public MainConfig() {
        super("config.yml", Restored.getInstance(), true);
    }

    @Override
    public void init() {
        getLinkedChestMaxDistance();
        getMagnetInterval();
        getHopperInterval();
        getCompactorInterval();
        getRocketInterval();
    }

    /**
     * Tick intervals for the periodic augment processors. Each pass walks networks or
     * players and touches live inventories, so these are the main lever for tick cost on
     * a busy server. Raising a value trades responsiveness for tick time; minimum 1.
     */
    public int getMagnetInterval() {
        return interval(MAGNET_INTERVAL_PATH, DEFAULT_MAGNET_INTERVAL);
    }

    public int getHopperInterval() {
        return interval(HOPPER_INTERVAL_PATH, DEFAULT_HOPPER_INTERVAL);
    }

    public int getCompactorInterval() {
        return interval(COMPACTOR_INTERVAL_PATH, DEFAULT_COMPACTOR_INTERVAL);
    }

    public int getRocketInterval() {
        return interval(ROCKET_INTERVAL_PATH, DEFAULT_ROCKET_INTERVAL);
    }

    private int interval(String path, int fallback) {
        reloadResource();
        int ticks = getOrSetDefault(path, fallback);
        return ticks < 1 ? fallback : ticks;
    }

    /**
     * Maximum distance, in blocks, between a network chest and linked storage.
     * A value of {@code -1} disables the distance limit.
     */
    public int getLinkedChestMaxDistance() {
        reloadResource();
        int distance = getOrSetDefault(
                LINKED_CHEST_MAX_DISTANCE_PATH,
                DEFAULT_LINKED_CHEST_MAX_DISTANCE
        );
        return distance < -1 ? DEFAULT_LINKED_CHEST_MAX_DISTANCE : distance;
    }

}
