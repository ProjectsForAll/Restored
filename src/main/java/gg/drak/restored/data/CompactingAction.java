package gg.drak.restored.data;

/** Direction in which a compacting configuration transforms its selected item. */
public enum CompactingAction {
    DECOMPACT,
    COMPACT;

    public CompactingAction toggle() {
        return this == DECOMPACT ? COMPACT : DECOMPACT;
    }
}
