package host.plas.restored.data.screens;

import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.jetbrains.annotations.NotNull;

@Getter @Setter
public class Slot implements Comparable<Slot> {
    private int index;
    private Icon icon;

    public Slot(int index, Icon icon) {
        this.index = index;
        this.icon = icon;
    }

    public Slot(int index) {
        this(index, null);
    }

    @Override
    public int compareTo(@NotNull Slot o) {
        return Integer.compare(index, o.getIndex());
    }
}