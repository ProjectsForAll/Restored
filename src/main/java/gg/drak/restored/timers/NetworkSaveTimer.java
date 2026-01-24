package gg.drak.restored.timers;

import host.plas.bou.scheduling.BaseRunnable;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;

public class NetworkSaveTimer extends BaseRunnable {
    public NetworkSaveTimer() {
        super(0, 20 * 60);
    }

    @Override
    public void run() {
        // Save the network.
        NetworkManager.getNetworks().forEach(Network::onSave);
    }
}
