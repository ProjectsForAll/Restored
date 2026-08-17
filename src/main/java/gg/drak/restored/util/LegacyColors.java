package gg.drak.restored.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts {@code #RRGGBB} and {@code &} color codes to section-sign legacy strings for inventory titles/lore.
 */
public final class LegacyColors {

    private static final Pattern BARE_HEX = Pattern.compile("#([0-9A-Fa-f]{6})");
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private LegacyColors() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            Component component = AMPERSAND.deserialize(normalizeHexCodes(input));
            return SECTION.serialize(component);
        } catch (Exception ignored) {
            return input.replace('&', '§');
        }
    }

    private static String normalizeHexCodes(String input) {
        Matcher matcher = BARE_HEX.matcher(input);
        StringBuffer buffer = new StringBuffer(input.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("&#" + matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
