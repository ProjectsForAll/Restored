package host.plas.restored.config;

import gg.drak.thebase.storage.resources.flat.simple.SimpleConfiguration;
import host.plas.restored.Restored;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", Restored.getInstance(), false);
    }

    @Override
    public void init() {

    }


}
