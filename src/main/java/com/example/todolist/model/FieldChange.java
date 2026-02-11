package com.example.todolist.model;

import lombok.*;

import java.io.Serializable;

/**
 * Represents a single field change in an audit log entry.
 * Stored as part of JSONB column in UserActionLogs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldChange implements Serializable {

    /**
     * Name of the field that changed
     */
    private String fieldName;

    /**
     * Previous value (before the change)
     * Null if this is a CREATE operation or field was previously null
     */
    private Object oldValue;

    /**
     * New value (after the change)
     * Null if field was set to null or this is a DELETE operation
     */
    private Object newValue;

    /**
     * Data type of the field (for display purposes)
     */
    private String fieldType;
}
