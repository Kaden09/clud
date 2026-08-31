package dev.sharing.clud.error;

public class StorageIntegrationPendingException extends RuntimeException {

	public StorageIntegrationPendingException() {
		super("Public download is not available until the Storage Service contract is implemented");
	}
}
