package gg.drak.restored.data.screens.items;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter @Setter
public class ViewerPage implements Comparable<ViewerPage> {
    private int index;
    private List<StoredItem> contents;

    public ViewerPage(int index, List<StoredItem> contents) {
        this.index = index;
        this.contents = contents;
    }

    @Override
    public int compareTo(@NotNull ViewerPage o) {
        return Integer.compare(index, o.getIndex());
    }
}
