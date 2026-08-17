package gg.drak.restored.data;

public enum NetworkRole {
    BLOCKED,
    READ_ONLY,
    MEMBER,
    ADMIN;

    public boolean canAccess() {
        return this != BLOCKED;
    }

    public boolean canDeposit() {
        return this == MEMBER || this == ADMIN;
    }

    public boolean canWithdraw() {
        return this == MEMBER || this == ADMIN;
    }

    public boolean canUseAugments() {
        return this == MEMBER || this == ADMIN;
    }

    public boolean canManage() {
        return this == ADMIN;
    }

    public NetworkRole next() {
        if (this == BLOCKED) {
            return READ_ONLY;
        }
        if (this == READ_ONLY) {
            return MEMBER;
        }
        if (this == MEMBER) {
            return ADMIN;
        }
        return BLOCKED;
    }

    public String displayName() {
        if (this == BLOCKED) {
            return "Blocked";
        }
        if (this == READ_ONLY) {
            return "Read Only";
        }
        if (this == MEMBER) {
            return "Member";
        }
        return "Admin";
    }
}
