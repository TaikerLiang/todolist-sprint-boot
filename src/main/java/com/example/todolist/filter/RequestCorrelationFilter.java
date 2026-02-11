package com.example.todolist.filter;

import com.example.todolist.util.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to generate or extract request correlation IDs for distributed tracing.
 *
 * <p>Runs early in the filter chain (Order 1) to ensure request IDs are available
 * for all subsequent filters and application code.
 *
 * <p>Request IDs are:
 * <ul>
 *   <li>Extracted from X-Request-Id header if present</li>
 *   <li>Generated as UUIDs if not provided</li>
 *   <li>Stored in MDC for automatic logging</li>
 *   <li>Stored in RequestContext for application access</li>
 *   <li>Returned in X-Request-Id response header</li>
 * </ul>
 */
@Component
@Slf4j
@Order(1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = extractOrGenerateRequestId(request);

        try {
            // Store in MDC for automatic logging
            MDC.put(MDC_REQUEST_ID_KEY, requestId);

            // Store in RequestContext for application access
            RequestContext.setRequestId(requestId);

            // Return in response header for client correlation
            response.setHeader(REQUEST_ID_HEADER, requestId);

            log.debug("Request correlation ID: {}", requestId);

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: Always clear to prevent memory leaks
            MDC.remove(MDC_REQUEST_ID_KEY);
            RequestContext.clear();
        }
    }

    /**
     * Extracts request ID from header or generates a new UUID.
     *
     * @param request the HTTP request
     * @return the request ID (never null)
     */
    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
            log.trace("Generated new request ID: {}", requestId);
        } else {
            log.trace("Using client-provided request ID: {}", requestId);
        }

        return requestId;
    }
}
