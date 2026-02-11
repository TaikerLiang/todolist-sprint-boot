package com.example.todolist.util;

import com.example.todolist.model.FieldChange;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for comparing entity objects and detecting field-level changes.
 *
 * <p>Uses reflection to compare all fields between two entity instances,
 * generating a map of {@link FieldChange} objects for audit logging.
 */
@Slf4j
public final class EntityComparator {

    private EntityComparator() {
        // Utility class - prevent instantiation
    }

    /**
     * Compares two entity instances and returns field-level changes.
     *
     * @param oldEntity the original entity (before update)
     * @param newEntity the updated entity (after update)
     * @return map of field changes (fieldName -> FieldChange), empty if no changes detected
     */
    public static Map<String, FieldChange> compareEntities(Object oldEntity, Object newEntity) {
        Map<String, FieldChange> changes = new HashMap<>();

        if (oldEntity == null || newEntity == null) {
            log.warn("Cannot compare null entities");
            return changes;
        }

        if (!oldEntity.getClass().equals(newEntity.getClass())) {
            log.warn("Cannot compare entities of different types: {} vs {}",
                    oldEntity.getClass().getName(), newEntity.getClass().getName());
            return changes;
        }

        Class<?> entityClass = oldEntity.getClass();
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            // Skip fields we don't want to audit
            if (shouldSkipField(field.getName())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object oldValue = field.get(oldEntity);
                Object newValue = field.get(newEntity);

                // Check if values are different
                if (!areEqual(oldValue, newValue)) {
                    FieldChange change = FieldChange.builder()
                            .fieldName(field.getName())
                            .oldValue(oldValue)
                            .newValue(newValue)
                            .fieldType(field.getType().getSimpleName())
                            .build();

                    changes.put(field.getName(), change);

                    log.debug("Field change detected: {} changed from {} to {}",
                            field.getName(), oldValue, newValue);
                }
            } catch (IllegalAccessException e) {
                log.error("Failed to access field {} for comparison: {}",
                        field.getName(), e.getMessage());
            }
        }

        return changes;
    }

    /**
     * Checks if two values are equal, handling null cases.
     */
    private static boolean areEqual(Object oldValue, Object newValue) {
        if (oldValue == null && newValue == null) {
            return true;
        }
        if (oldValue == null || newValue == null) {
            return false;
        }
        return oldValue.equals(newValue);
    }

    /**
     * Determines if a field should be skipped during comparison.
     *
     * <p>Skips:
     * <ul>
     *   <li>id - Primary keys shouldn't change</li>
     *   <li>createdAt - Creation timestamp is immutable</li>
     *   <li>updatedAt - Auto-managed by JPA lifecycle</li>
     * </ul>
     */
    private static boolean shouldSkipField(String fieldName) {
        return fieldName.equals("id") ||
               fieldName.equals("createdAt") ||
               fieldName.equals("updatedAt");
    }
}
