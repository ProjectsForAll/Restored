package host.plas.restored.data.permission;

import lombok.Getter;

@Getter
public enum PermissionNode {
    NETWORK_ACCESS("network.access"),
    NETWORK_TRUST("network.trust"),
    NETWORK_BREAK("network.break"),
    NETWORK_PLACE("network.place"),

    ERROR("error"),
    ;

    private String node;

    PermissionNode(String node) {
        this.node = node;
    }
}
