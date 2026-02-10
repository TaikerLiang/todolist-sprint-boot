package com.example.todolist.audit;

import com.example.todolist.model.FieldChange;
import jakarta.persistence.Transient;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility for computing field-level diffs between entity snapshots
 *
 * Uses reflection to compare field values and generate FieldChange objects
 */
public class FieldComparator {

    private static final Set<String> EXCLUDED_FIELDS = Set.of(
        "password",      // Never audit passwords
        "secretKey",     // Never audit secret keys
        "privateKey"     // Never audit private keys
    );

    /**
     * Capture current state of all auditable fields in an entity
     *
     * @param entity The entity to snapshot
     * @return Map of field names to current values
     */
    public static Map<String, Object> captureSnapshot(Object entity) {
        Map<String, Object> snapshot = new HashMap<>();
        Class<?> clazz = entity.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            if (shouldAuditField(field)) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    snapshot.put(field.getName(), value);
                } catch (IllegalAccessException e) {
                    // Skip fields that can't be accessed
                }
            }
        }

        return snapshot;
    }

    /**
     * Compute field-level changes between old and new entity states
     *
     * @param oldSnapshot Snapshot of entity before modification
     * @param newEntity Current entity state
     * @return Map of field names to FieldChange objects (only changed fields)
     */
    public static Map<String, FieldChange> computeChanges(
            Map<String, Object> oldSnapshot,
            Object newEntity) {

        Map<String, FieldChange> changes = new HashMap<>();
        Class<?> clazz = newEntity.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            if (shouldAuditField(field)) {
                try {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    Object oldValue = oldSnapshot.get(fieldName);
                    Object newValue = field.get(newEntity);

                    // Only record if value actually changed
                    if (!Objects.equals(oldValue, newValue)) {
                        changes.put(fieldName, new FieldChange(oldValue, newValue));
                    }
                } catch (IllegalAccessException e) {
                    // Skip fields that can't be accessed
                }
            }
        }

        return changes;
    }

    /**
     * Capture final state of entity before deletion
     *
     * @param entity The entity being deleted
     * @return Map of field names to FieldChange objects with old=value, new=null
     */
    public static Map<String, FieldChange> captureDeletionState(Object entity) {
        Map<String, FieldChange> changes = new HashMap<>();
        Class<?> clazz = entity.getClass();

        for (Field field : clazz.getDeclaredFields()) {
            if (shouldAuditField(field)) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    changes.put(field.getName(), new FieldChange(value, null));
                } catch (IllegalAccessException e) {
                    // Skip fields that can't be accessed
                }
            }
        }

        return changes;
    }

    /**
     * Determine if a field should be audited
     *
     * @param field The field to check
     * @return true if field should be audited, false otherwise
     */
    private static boolean shouldAuditField(Field field) {
        // Skip transient fields
        if (field.isAnnotationPresent(Transient.class)) {
            return false;
        }

        // Skip excluded sensitive fields
        if (EXCLUDED_FIELDS.contains(field.getName())) {
            return false;
        }

        // Skip static and synthetic fields
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
            field.isSynthetic()) {
            return false;
        }

        return true;
    }
}
