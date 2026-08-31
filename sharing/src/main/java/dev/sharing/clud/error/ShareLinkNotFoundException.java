package dev.sharing.clud.error;

public class ShareLinkNotFoundException extends RuntimeException {

	public ShareLinkNotFoundException() {
		super("Share link was not found");
	}
}
