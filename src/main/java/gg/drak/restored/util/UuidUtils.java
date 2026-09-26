package gg.drak.restored.util;

import java.util.Optional;
import java.util.UUID;

/** Safe parsing for UUIDs read from user-controlled or persisted metadata. */
public final class UuidUtils {

    private UuidUtils() {
    }

    public static Optional<UUID> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
