package gg.drak.restored.serialization;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gg.drak.restored.Restored;
import host.plas.bou.gui.items.ItemData;
import host.plas.bou.utils.VersionTool;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads item payloads stored in the DB. Paper 1.20.5+ rejects legacy Bukkit {@code ItemStack.serialize()}
 * JSON that includes a top-level {@code meta} key; BukkitOfUtils still produced that shape when
 * {@code VersionTool#getServerVersion()} is empty.
 */
public final class PersistedItemCodec {

    private static final Gson GSON = new Gson();

    private PersistedItemCodec() {
    }

    public static ItemStack deserializePayload(String itemDataStr) {
        if (itemDataStr == null || itemDataStr.isBlank() || "{}".equals(itemDataStr.trim())) {
            return new ItemStack(Material.BARRIER);
        }
        String trimmed = itemDataStr.trim();

        // Pre-1.20.5 Spigot map shape: top-level "meta" — Paper rejects it in ItemStack.deserialize(Map)
        if (trimmed.startsWith("{") && trimmed.contains("\"meta\"")) {
            try {
                ItemStack legacy = fromLegacyBukkitSerializeJson(trimmed);
                if (legacy != null && legacy.getType() != Material.AIR) {
                    return legacy;
                }
            } catch (Throwable t) {
                Restored.getInstance().logWarning("PersistedItemCodec: legacy meta JSON parse failed: " + t.getMessage());
            }
        }

        try {
            ItemStack viaBou = ItemData.deserialize(trimmed);
            if (viaBou != null && viaBou.getType() != Material.AIR) {
                return viaBou;
            }
        } catch (Throwable ignored) {
        }

        if (trimmed.startsWith("{")) {
            try {
                ItemStack retry = fromLegacyBukkitSerializeJson(trimmed);
                if (retry != null && retry.getType() != Material.AIR) {
                    return retry;
                }
            } catch (Throwable ignored) {
            }
        }

        Restored.getInstance().logWarning("PersistedItemCodec: could not deserialize item payload (using barrier).");
        return new ItemStack(Material.BARRIER);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack fromLegacyBukkitSerializeJson(String json) {
        Map<String, Object> raw = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {
        }.getType());
        if (raw == null) {
            return null;
        }

        Object metaRaw = raw.get("meta");
        Map<String, Object> withoutMeta = new LinkedHashMap<>(raw);
        withoutMeta.remove("meta");

        ItemStack stack = null;
        try {
            stack = ItemStack.deserialize(sanitizeDeserializeMap(withoutMeta));
        } catch (Throwable ignored) {
        }

        if (stack == null || stack.getType() == Material.AIR) {
            stack = stackFromMaterialAmount(raw);
        }

        if (stack == null) {
            return null;
        }

        if (metaRaw instanceof Map) {
            try {
                ItemMeta meta = VersionTool.deserializeItemMetaFallback(stack, (Map<String, Object>) metaRaw);
                if (meta != null) {
                    stack.setItemMeta(meta);
                }
            } catch (Throwable ignored) {
            }
        }

        return stack;
    }

    /**
     * Paper's deserializer is picky about numeric types (e.g. Double vs Integer).
     */
    private static Map<String, Object> sanitizeDeserializeMap(Map<String, Object> map) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if ("amount".equals(e.getKey()) && v instanceof Number) {
                out.put(e.getKey(), ((Number) v).intValue());
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static ItemStack stackFromMaterialAmount(Map<String, Object> map) {
        Object typeObj = map.get("type");
        if (typeObj == null) {
            return null;
        }
        String typeName = typeObj.toString();
        Material mat = Material.matchMaterial(typeName);
        if (mat == null) {
            mat = Material.matchMaterial(typeName.toUpperCase(Locale.ROOT));
        }
        if (mat == null || mat.isAir()) {
            return null;
        }

        int amount = 1;
        Object amtObj = map.get("amount");
        if (amtObj instanceof Number) {
            amount = Math.max(1, ((Number) amtObj).intValue());
        } else if (amtObj != null) {
            try {
                amount = Math.max(1, Integer.parseInt(amtObj.toString()));
            } catch (NumberFormatException ignored) {
            }
        }

        ItemStack s = new ItemStack(mat);
        int max = mat.getMaxStackSize();
        s.setAmount(max > 0 ? Math.min(amount, max) : amount);
        return s;
    }
}
