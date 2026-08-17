package gg.drak.restored.timers;

import gg.drak.restored.data.NetworkManager;
import host.plas.bou.scheduling.BaseRunnable;

public class NetworkSaveTimer extends BaseRunnable {

    public NetworkSaveTimer() {
        // Every 5 seconds: queue dirty networks; middleware flushes async every 1s.
        super(100, 100);
    }

    @Override
    public void run() {
        NetworkManager.saveAllDirty();
    }
}
