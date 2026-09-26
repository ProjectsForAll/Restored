package gg.drak.restored.serialization;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gg.drak.restored.Restored;
import host.plas.bou.gui.items.ItemData;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes/deserializes item payloads for DB storage and identity keys.
 * <p>
 * Prefer Paper's {@link ItemStack#serializeAsBytes()} so item components / meta are preserved.
 * BukkitOfUtils {@code ItemUtils#getItemNBT} uses legacy NMS reflection that fails or strips
 * components on Paper 1.20.5+, which caused different-meta stacks to share one storage key.
 */
public final class PersistedItemCodec {

    private static final Gson GSON = new Gson();
    private static final String PREFIX_PB64 = "pb64:";

    private PersistedItemCodec() {
    }

    /**
     * True when {@code stack} cannot possibly have {@code itemKey}, decided without hashing.
     * <p>
     * {@link #itemKey} costs a clone, a full component serialization and a SHA-256 digest. Scans
     * that look for one specific key used to pay that on every slot of every linked chest merely
     * to reject it. Material is part of the serialized form, so a Material mismatch is a
     * guaranteed key mismatch: this rejects almost every slot for free, and the digest is then
     * computed only to confirm the few survivors. Identity is unchanged — this never decides a
     * match, only a non-match.
     */
    public static boolean cannotMatch(ItemStack stack, Material keyMaterial) {
        return keyMaterial != null && stack != null && stack.getType() != keyMaterial;
    }

    /**
     * Stable identity for stacking: SHA-256 of Paper bytes with amount forced to 1.
     */
    public static String itemKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "air";
        }
        try {
            return sha256Hex(asTemplateBytes(stack));
        } catch (Throwable t) {
            Restored.getInstance().logWarning("PersistedItemCodec: itemKey failed, falling back: " + t.getMessage());
            ItemStack one = flatten(stack);
            return "fb:" + one.getType().name() + ":" + Integer.toHexString(one.hashCode());
        }
    }

    /**
     * Persistable template payload (amount normalized to 1).
     */
    public static String serializePayload(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "{}";
        }
        try {
            return PREFIX_PB64 + Base64.getEncoder().encodeToString(asTemplateBytes(stack));
        } catch (Throwable t) {
            Restored.getInstance().logWarning("PersistedItemCodec: serializeAsBytes failed: " + t.getMessage());
            return ItemData.serialize(flatten(stack));
        }
    }

    /** Decodes a payload, substituting a barrier for anything that cannot be decoded. */
    public static ItemStack deserializePayload(String itemDataStr) {
        ItemStack stack = tryDeserializePayload(itemDataStr);
        if (stack == null) {
            Restored.getInstance().logWarning("PersistedItemCodec: could not deserialize item payload (using barrier).");
            return new ItemStack(Material.BARRIER);
        }
        return stack;
    }

    /**
     * Decodes a payload, or returns null when it cannot be decoded on this server — typically an
     * item that references registry entries (datapack jukebox songs, enchantments, ...) which
     * are not currently loaded. Callers holding stored data must keep the original payload in
     * that case rather than persist a substitute.
     */
    public static ItemStack tryDeserializePayload(String itemDataStr) {
        if (itemDataStr == null || itemDataStr.isBlank() || "{}".equals(itemDataStr.trim())) {
            return null;
        }
        String trimmed = itemDataStr.trim();

        if (trimmed.startsWith(PREFIX_PB64)) {
            // A pb64 payload is only ever Paper bytes; the JSON fallbacks below cannot read it
            // and only flood the log with parser stack traces.
            try {
                byte[] bytes = Base64.getDecoder().decode(trimmed.substring(PREFIX_PB64.length()));
                ItemStack stack = ItemStack.deserializeBytes(bytes);
                if (stack != null && stack.getType() != Material.AIR) {
                    stack.setAmount(1);
                    return stack;
                }
            } catch (Throwable t) {
                Restored.getInstance().logWarning("PersistedItemCodec: pb64 decode failed: " + t.getMessage());
            }
            return null;
        }

        // Pre-1.20.5 Spigot map shape: top-level "meta"
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
                viaBou.setAmount(1);
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

        return null;
    }

    private static byte[] asTemplateBytes(ItemStack stack) {
        return flatten(stack).serializeAsBytes();
    }

    private static ItemStack flatten(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            return HexFormat.of().formatHex(bytes);
        }
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
            stack = ItemStack.deserialize(sanitizeMap(withoutMeta));
        } catch (Throwable ignored) {
        }

        if (stack == null || stack.getType() == Material.AIR) {
            stack = stackFromMaterialAmount(raw);
        }

        if (stack == null) {
            return null;
        }

        if (metaRaw instanceof Map<?, ?> metaMap) {
            applyLegacyMetaQuietly(stack, (Map<String, Object>) metaMap);
        }

        stack.setAmount(1);
        return stack;
    }

    /**
     * Applies what we can from legacy Bukkit meta maps without calling BukkitOfUtils
     * (its fallback logs noisy Gson errors on Paper Adventure/component JSON).
     */
    private static void applyLegacyMetaQuietly(ItemStack stack, Map<String, Object> metaMap) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || metaMap == null || metaMap.isEmpty()) {
            return;
        }

        // Prefer Bukkit configuration serialization when the map is well-formed.
        try {
            Map<String, Object> sanitized = sanitizeMap(metaMap);
            if (!sanitized.containsKey("==") && sanitized.containsKey("meta-type")) {
                sanitized.put("==", "ItemMeta");
            }
            Object deserialized = ConfigurationSerialization.deserializeObject(sanitized);
            if (deserialized instanceof ItemMeta itemMeta) {
                stack.setItemMeta(itemMeta);
                return;
            }
        } catch (Throwable ignored) {
        }

        boolean changed = false;

        Object displayName = metaMap.get("display-name");
        if (displayName instanceof String name && !name.isBlank()) {
            meta.setDisplayName(stripComponentNoise(name));
            changed = true;
        }

        List<String> lore = coerceStringList(metaMap.get("lore"));
        if (!lore.isEmpty()) {
            List<String> cleaned = new ArrayList<>(lore.size());
            for (String line : lore) {
                cleaned.add(stripComponentNoise(line));
            }
            meta.setLore(cleaned);
            changed = true;
        }

        Object enchantsObj = metaMap.get("enchants");
        if (enchantsObj instanceof Map<?, ?> enchants) {
            for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                if (entry.getKey() == null || !(entry.getValue() instanceof Number level)) {
                    continue;
                }
                Enchantment enchantment = Enchantment.getByName(String.valueOf(entry.getKey()));
                if (enchantment == null) {
                    try {
                        enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT)));
                    } catch (Throwable ignored) {
                    }
                }
                if (enchantment != null) {
                    meta.addEnchant(enchantment, level.intValue(), true);
                    changed = true;
                }
            }
        }

        if (changed) {
            stack.setItemMeta(meta);
        }
    }

    private static String stripComponentNoise(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replace("\\", "");
        // Adventure JSON blobs sometimes land here as quoted strings; keep raw text if parse-looking.
        if (cleaned.startsWith("{") && cleaned.contains("\"text\"")) {
            try {
                Map<String, Object> map = GSON.fromJson(cleaned, new TypeToken<Map<String, Object>>() {
                }.getType());
                if (map != null && map.get("text") != null) {
                    return String.valueOf(map.get("text"));
                }
            } catch (Throwable ignored) {
            }
        }
        return cleaned;
    }

    private static List<String> coerceStringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            if (entry instanceof String s) {
                out.add(s);
            } else if (entry instanceof List<?> nested) {
                // Gson occasionally nests lore lines oddly; flatten one level.
                for (Object inner : nested) {
                    if (inner instanceof String s) {
                        out.add(s);
                    } else if (inner != null) {
                        out.add(String.valueOf(inner));
                    }
                }
            } else if (entry != null) {
                out.add(String.valueOf(entry));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<String, Object> map) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            out.put(e.getKey(), sanitizeValue(e.getKey(), e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(String key, Object v) {
        if (v instanceof Map<?, ?> nested) {
            Map<String, Object> child = new HashMap<>();
            for (Map.Entry<?, ?> e : nested.entrySet()) {
                child.put(String.valueOf(e.getKey()), sanitizeValue(String.valueOf(e.getKey()), e.getValue()));
            }
            return child;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object entry : list) {
                out.add(sanitizeValue(key, entry));
            }
            return out;
        }
        if (v instanceof Number number) {
            // Paper is picky: amount/damage/custom-model-data expect ints.
            if ("amount".equals(key) || "damage".equals(key) || "custom-model-data".equals(key)
                    || "repair-cost".equals(key) || key.toLowerCase(Locale.ROOT).contains("level")) {
                return number.intValue();
            }
            double d = number.doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return number.intValue();
            }
            return number;
        }
        return v;
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

        ItemStack s = new ItemStack(mat);
        s.setAmount(1);
        return s;
    }
}
