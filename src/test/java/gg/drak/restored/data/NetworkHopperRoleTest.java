package gg.drak.restored.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NetworkHopperRoleTest {

    @Test
    void parsesCanonicalAndEnumRoleIds() {
        assertEquals(NetworkHopperRole.INPUT, NetworkHopperRole.fromId("input"));
        assertEquals(NetworkHopperRole.OUTPUT, NetworkHopperRole.fromId("OUTPUT"));
        assertEquals(NetworkHopperRole.INPUT, NetworkHopperRole.fromId("InPuT"));
    }

    @Test
    void rejectsUnknownRoleIds() {
        assertNull(NetworkHopperRole.fromId(null));
        assertNull(NetworkHopperRole.fromId("sideways"));
    }
}
