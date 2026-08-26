package dev.identity.clud.user;

public enum Role {
    ADMIN,
    USER;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
