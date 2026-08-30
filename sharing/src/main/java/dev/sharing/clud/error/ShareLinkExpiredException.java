package dev.sharing.clud.error;

public class ShareLinkExpiredException extends RuntimeException {

	public ShareLinkExpiredException() {
		super("The public link has expired");
	}
}
