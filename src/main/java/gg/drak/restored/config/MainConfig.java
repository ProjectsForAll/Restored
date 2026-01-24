package gg.drak.restored.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import gg.drak.restored.Restored;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", Restored.getInstance(), false);
    }

    @Override
    public void init() {

    }


}
