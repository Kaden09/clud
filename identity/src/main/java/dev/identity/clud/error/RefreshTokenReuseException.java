package dev.identity.clud.error;

public class RefreshTokenReuseException extends InvalidTokenException {
    public RefreshTokenReuseException(String message) {
        super(message);
    }
}
