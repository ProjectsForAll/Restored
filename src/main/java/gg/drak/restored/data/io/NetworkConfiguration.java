package gg.drak.restored.data.io;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import gg.drak.restored.Restored;

public class NetworkConfiguration extends SimpleConfiguration {
    private String identifier;

    public NetworkConfiguration() {
        super("network-config.yml", Restored.getInstance(), false);
    }

    @Override
    public void init() {
        // Initialize network configuration
    }
}
