package gg.drak.restored.timers;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.Tickable;
import host.plas.bou.scheduling.BaseRunnable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs every tick (1 server tick = 50ms) and ticks all Tickable network blocks
 * at their configured tick rates.
 */
public class NetworkTickTimer extends BaseRunnable {
    private final ConcurrentHashMap<String, Integer> tickCounters = new ConcurrentHashMap<>();

    public NetworkTickTimer() {
        super(0, 1); // Run every tick
    }

    public static void removeCounter(String blockId) {
        if (Restored.getNetworkTickTimer() != null) {
            Restored.getNetworkTickTimer().clearCounter(blockId);
        }
    }

    public void clearCounter(String blockId) {
        tickCounters.remove(blockId);
    }

    @Override
    public void run() {
        for (Network network : NetworkManager.getNetworks()) {
            for (NetworkBlock block : network.getBlocks()) {
                if (!(block instanceof Tickable)) continue;

                Tickable tickable = (Tickable) block;
                String id = block.getIdentifier();
                int counter = tickCounters.getOrDefault(id, 0) + 1;

                if (counter >= tickable.getTickRate()) {
                    tickable.onTick();
                    counter = 0;
                }

                tickCounters.put(id, counter);
            }
        }
    }
}
