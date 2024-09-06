package host.plas.restored.config;

import host.plas.restored.Restored;
import tv.quaint.storage.resources.flat.simple.SimpleConfiguration;

public class MainConfig extends SimpleConfiguration {
    public MainConfig() {
        super("config.yml", Restored.getInstance(), false);
    }

    @Override
    public void init() {

    }
}
