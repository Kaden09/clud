package dev.sharing.clud.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

	@Bean
	RestClient fileServiceRestClient(
			@Value("${clud.sharing.file-service-url}") String baseUrl,
			@Value("${clud.sharing.http.connect-timeout:2s}") Duration connectTimeout,
			@Value("${clud.sharing.http.read-timeout:5s}") Duration readTimeout) {
		return restClient(baseUrl, connectTimeout, readTimeout);
	}

	@Bean
	RestClient storageServiceRestClient(
			@Value("${clud.sharing.storage-service-url}") String baseUrl,
			@Value("${clud.sharing.storage-http.connect-timeout:2s}") Duration connectTimeout,
			@Value("${clud.sharing.storage-http.read-timeout:30s}") Duration readTimeout) {
		return restClient(baseUrl, connectTimeout, readTimeout);
	}

	private RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);

		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}
}
