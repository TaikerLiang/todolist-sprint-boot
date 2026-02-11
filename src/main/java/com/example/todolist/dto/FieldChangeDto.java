package com.example.todolist.dto;

import lombok.*;

/**
 * DTO representing a single field change in an audit log entry.
 *
 * <p>Used in API responses to show before/after values for field-level changes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldChangeDto {

    /**
     * Name of the field that changed (e.g., "title", "completed").
     */
    private String fieldName;

    /**
     * Previous value before the change (null for CREATE or when field was null).
     */
    private Object oldValue;

    /**
     * New value after the change (null for DELETE or when field set to null).
     */
    private Object newValue;

    /**
     * Data type of the field (e.g., "String", "Boolean", "Integer").
     */
    private String fieldType;
}
