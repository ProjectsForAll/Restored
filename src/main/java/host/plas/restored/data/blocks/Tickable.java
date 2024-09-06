package host.plas.restored.data.blocks;

public interface Tickable {
    int getTickRate();

    void setTickRate(int tickRate);

    void onTick();
}
