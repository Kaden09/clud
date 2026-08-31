package dev.sharing.clud.client;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.sharing.clud.error.InvalidShareOperationException;
import dev.sharing.clud.error.SharedFileNotFoundException;
import dev.sharing.clud.error.UpstreamServiceException;

@Component
@RequiredArgsConstructor
public class FileServiceClient {

	private static final String USER_ID_HEADER = "X-User-ID";

	private final RestClient fileServiceRestClient;

	public FileNodeResponse getActiveFile(UUID ownerId, UUID fileId) {
		try {
			FileNodeResponse response = fileServiceRestClient.get()
					.uri("/nodes/{nodeId}", fileId)
					.header(USER_ID_HEADER, ownerId.toString())
					.retrieve()
					.onStatus(HttpStatusCode::is5xxServerError, (request, upstreamResponse) -> {
						throw new UpstreamServiceException("File Service is unavailable", null);
					})
					.body(FileNodeResponse.class);
			if (response == null) {
				throw new UpstreamServiceException("File Service returned an empty response", null);
			}
			if (response.type() != FileNodeResponse.NodeType.FILE) {
				throw new InvalidShareOperationException("Only files can have public links");
			}
			return response;
		}
		catch (HttpClientErrorException.NotFound exception) {
			throw new SharedFileNotFoundException(fileId);
		}
		catch (InvalidShareOperationException | SharedFileNotFoundException | UpstreamServiceException exception) {
			throw exception;
		}
		catch (RestClientException exception) {
			throw new UpstreamServiceException("File Service is unavailable", exception);
		}
	}
}
