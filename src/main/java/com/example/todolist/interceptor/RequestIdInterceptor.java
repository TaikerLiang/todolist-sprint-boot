package com.example.todolist.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Interceptor for capturing or generating request IDs for audit correlation
 *
 * - If client provides X-Request-ID header, use that
 * - Otherwise, generate a new UUID
 * - Echo the request ID back in the response header
 * - Store in ThreadLocal for access by audit logging
 */
@Component
@Slf4j
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<UUID> REQUEST_ID = new ThreadLocal<>();
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * Get the current request ID for this thread
     *
     * @return UUID of current request, or null if not set
     */
    public static UUID getCurrentRequestId() {
        return REQUEST_ID.get();
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // Use client-provided request ID if present, otherwise generate
        String requestIdHeader = request.getHeader(REQUEST_ID_HEADER);
        UUID requestId;

        if (requestIdHeader != null && !requestIdHeader.trim().isEmpty()) {
            try {
                requestId = UUID.fromString(requestIdHeader);
                log.debug("Using client-provided request ID: {}", requestId);
            } catch (IllegalArgumentException e) {
                // Invalid UUID format, generate new one
                requestId = UUID.randomUUID();
                log.debug("Invalid request ID format, generated new: {}", requestId);
            }
        } else {
            requestId = UUID.randomUUID();
            log.debug("Generated new request ID: {}", requestId);
        }

        REQUEST_ID.set(requestId);

        // Echo back in response for debugging
        response.setHeader(REQUEST_ID_HEADER, requestId.toString());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // Clean up ThreadLocal to prevent memory leaks
        REQUEST_ID.remove();
    }
}
