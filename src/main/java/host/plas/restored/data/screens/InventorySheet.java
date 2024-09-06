package host.plas.restored.data.screens;

import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Getter @Setter
    public class InventorySheet {
        private int size; // amount of slots
        private ConcurrentSkipListSet<Slot> slots; // slot, icon

        public InventorySheet(int slots) {
            this.size = slots;
            this.slots = new ConcurrentSkipListSet<>();
        }

        public void addIcon(int slot, Icon icon) {
            this.slots.add(new Slot(slot, icon));
        }

        public void removeIcon(int slot) {
            this.slots.removeIf(s -> s.getIndex() == slot);
        }

        public Slot getSlot(int slot) {
            AtomicReference<Slot> slotReference = new AtomicReference<>();

            this.slots.forEach(s -> {
                if (s.getIndex() == slot) {
                    slotReference.set(s);
                }
            });

            return slotReference.get();
        }

        public int getRows() {
            return (int) Math.ceil(size / 9.0);
        }

        public void forEachSlot(Consumer<Slot> consumer) {
            this.slots.forEach(consumer);
        }
    }