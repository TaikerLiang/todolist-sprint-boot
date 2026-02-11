package com.example.todolist.model;

/**
 * Type of operation performed on an audited entity
 */
public enum AuditOperation {
    /**
     * Entity was created
     */
    CREATE,

    /**
     * Entity was modified
     */
    UPDATE,

    /**
     * Entity was deleted
     */
    DELETE
}
