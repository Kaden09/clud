package dev.gateway.clud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-ID";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
		}

		HttpServletResponse wrappedResponse = new RequestIdHeaderResponse(response, requestId);
		wrappedResponse.setHeader(REQUEST_ID_HEADER, requestId);

		long startedAt = System.nanoTime();
    
		log.info("Gateway request started: requestId={} method={} path={}",
				requestId, request.getMethod(), request.getRequestURI());
		try {
			filterChain.doFilter(new RequestIdHeaderRequest(request, requestId), wrappedResponse);
			log.info("Gateway request completed: requestId={} method={} path={} status={} durationMs={}",
					requestId,
					request.getMethod(),
					request.getRequestURI(),
					wrappedResponse.getStatus(),
					elapsedMilliseconds(startedAt));
		}
		catch (IOException | ServletException | RuntimeException exception) {
			log.warn("Gateway request failed: requestId={} method={} path={} status={} durationMs={} exception={}",
					requestId,
					request.getMethod(),
					request.getRequestURI(),
					wrappedResponse.getStatus(),
					elapsedMilliseconds(startedAt),
					exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private static long elapsedMilliseconds(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private static final class RequestIdHeaderRequest extends HttpServletRequestWrapper {

		private final String requestId;

		private RequestIdHeaderRequest(HttpServletRequest request, String requestId) {
			super(request);
			this.requestId = requestId;
		}

		@Override
		public String getHeader(String name) {
			return REQUEST_ID_HEADER.equalsIgnoreCase(name) ? requestId : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return REQUEST_ID_HEADER.equalsIgnoreCase(name)
					? Collections.enumeration(List.of(requestId))
					: super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = new ArrayList<>();
			Enumeration<String> originalNames = super.getHeaderNames();
			if (originalNames != null) {
				while (originalNames.hasMoreElements()) {
					String name = originalNames.nextElement();
					if (!REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
						names.add(name);
					}
				}
			}
			names.add(REQUEST_ID_HEADER);
			return Collections.enumeration(names);
		}
	}

	private static final class RequestIdHeaderResponse extends HttpServletResponseWrapper {

		private final String requestId;

		private RequestIdHeaderResponse(HttpServletResponse response, String requestId) {
			super(response);
			this.requestId = requestId;
		}

		@Override
		public void setHeader(String name, String value) {
			super.setHeader(name, REQUEST_ID_HEADER.equalsIgnoreCase(name) ? requestId : value);
		}

		@Override
		public void addHeader(String name, String value) {
			if (REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
				super.setHeader(REQUEST_ID_HEADER, requestId);
			}
			else {
				super.addHeader(name, value);
			}
		}
	}
}
