package com.example.todolist.audit;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local context for storing entity snapshots before updates
 *
 * Used by AuditEntityListener to capture the "before" state of entities
 * so field-level diffs can be computed during @PreUpdate
 */
public class AuditContext {

    private static final ThreadLocal<Map<Object, Map<String, Object>>> SNAPSHOTS =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * Store a snapshot of an entity's current state
     *
     * @param entity The entity to snapshot
     * @param snapshot Map of field names to values
     */
    public static void storeSnapshot(Object entity, Map<String, Object> snapshot) {
        SNAPSHOTS.get().put(entity, snapshot);
    }

    /**
     * Retrieve the stored snapshot for an entity
     *
     * @param entity The entity
     * @return Map of field names to values, or null if no snapshot exists
     */
    public static Map<String, Object> getSnapshot(Object entity) {
        return SNAPSHOTS.get().get(entity);
    }

    /**
     * Remove the snapshot for an entity
     *
     * @param entity The entity
     */
    public static void removeSnapshot(Object entity) {
        SNAPSHOTS.get().remove(entity);
    }

    /**
     * Clear all snapshots from the thread-local context
     * Should be called after transaction completion
     */
    public static void clear() {
        SNAPSHOTS.remove();
    }
}
