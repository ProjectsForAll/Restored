package host.plas.restored.data.screens.items;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.Map;

@Getter @Setter
public class ItemData {
    private String identifier;
    private BigInteger amount;
    private String data;

    public ItemData(String identifier, BigInteger amount, String data) {
        this.identifier = identifier;
        this.amount = amount;
        this.data = data;
    }

    public ItemData(String identifier, BigInteger amount, ItemStack stack) {
        this(identifier, amount, serialize(stack));
    }

    public ItemStack getStack() {
        return deserialize(data);
    }

    public StoredItem toStoredItem() {
        return new StoredItem(identifier, amount, getStack());
    }

    public static String serialize(ItemStack item) {
        Gson gson = new Gson();
        if (item == null) {
            return "null";
        }
        Map<String, Object> serializedItem = item.serialize();
        return gson.toJson(serializedItem);
    }

    public static ItemStack deserialize(String data) {
        Gson gson = new Gson();
        Map<String, Object> serializedItem = gson.fromJson(data, Map.class);
        return ItemStack.deserialize(serializedItem);
    }
}
