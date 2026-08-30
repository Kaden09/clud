package dev.sharing.clud.error;

public class UpstreamServiceException extends RuntimeException {

	public UpstreamServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
