package gg.drak.restored.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidUtilsTest {

    @Test
    void parsesValidUuid() {
        UUID expected = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals(expected, UuidUtils.parse(expected.toString()).orElseThrow());
    }

    @Test
    void rejectsMalformedAndBlankValues() {
        assertTrue(UuidUtils.parse(null).isEmpty());
        assertTrue(UuidUtils.parse(" ").isEmpty());
        assertTrue(UuidUtils.parse("not-a-uuid").isEmpty());
    }
}
