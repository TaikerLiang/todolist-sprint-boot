package com.example.todolist.model;

/**
 * Enumeration of audit log operation types
 *
 * Represents the type of operation performed on an audited entity.
 */
public enum AuditOperation {
    /**
     * Entity creation operation
     */
    INSERT,

    /**
     * Entity modification operation
     */
    UPDATE,

    /**
     * Entity deletion operation
     */
    DELETE
}
