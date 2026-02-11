package com.example.todolist.util;

/**
 * Thread-local storage for request-scoped data (request ID, user ID).
 *
 * <p>Used to propagate request correlation IDs and user information throughout
 * the application without polluting method signatures.
 *
 * <p><b>CRITICAL</b>: Always call {@link #clear()} in finally blocks to prevent
 * memory leaks from ThreadLocal storage.
 *
 * @see com.example.todolist.filter.RequestCorrelationFilter
 */
public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private RequestContext() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current request ID.
     *
     * @return the request ID, or null if not set
     */
    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    /**
     * Sets the request ID for the current thread.
     *
     * @param requestId the request ID (typically a UUID)
     */
    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    /**
     * Gets the current user ID.
     *
     * @return the user ID, or null if not authenticated
     */
    public static String getUserId() {
        return USER_ID.get();
    }

    /**
     * Sets the user ID for the current thread.
     *
     * @param userId the user ID from authentication context
     */
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    /**
     * Clears all thread-local data.
     *
     * <p><b>MUST</b> be called in finally blocks to prevent memory leaks.
     */
    public static void clear() {
        REQUEST_ID.remove();
        USER_ID.remove();
    }

    /**
     * Checks if request context is available.
     *
     * @return true if request ID is set, false otherwise
     */
    public static boolean hasRequestContext() {
        return REQUEST_ID.get() != null;
    }
}
