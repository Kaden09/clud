package dev.sharing.clud.error;

import java.util.UUID;

public class SharedFileNotFoundException extends RuntimeException {

	public SharedFileNotFoundException(UUID fileId) {
		super("File %s was not found".formatted(fileId));
	}
}
