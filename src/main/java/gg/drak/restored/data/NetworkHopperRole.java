package gg.drak.restored.data;

public enum NetworkHopperRole {
    INPUT("input"),
    OUTPUT("output");

    private final String id;

    NetworkHopperRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static NetworkHopperRole fromId(String id) {
        if (id == null) {
            return null;
        }
        for (NetworkHopperRole role : values()) {
            if (role.id.equalsIgnoreCase(id) || role.name().equalsIgnoreCase(id)) {
                return role;
            }
        }
        return null;
    }
}
