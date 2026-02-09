package com.example.todolist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents a before/after value pair for a single field that changed during an operation
 *
 * Used in JSONB structure: {"fieldName": {"old": value, "new": value}}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldChange implements Serializable {
    /**
     * Original value before the change (null if field was unset)
     */
    private Object old;

    /**
     * New value after the change (null if field was cleared)
     */
    private Object newValue;
}
