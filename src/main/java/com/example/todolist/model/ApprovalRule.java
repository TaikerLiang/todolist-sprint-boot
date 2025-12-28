package com.example.todolist.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class ApprovalRule {
    private String targetItemType;
    private RequestOperation operation;
    private String condition;  // e.g., "level=HIGH", null for unconditional
    private Map<Role, RoleRequirement> roleRequirements;  // Role -> mandatory/optional
    private Integer priority;  // Higher = more specific

    /**
     * Check if this rule matches the given item type, operation, and data
     */
    public boolean matches(String itemType, RequestOperation op, Map<String, Object> itemData) {
        // Check type and operation
        if (!this.targetItemType.equals(itemType) || this.operation != op) {
            return false;
        }

        // Check condition if present
        if (condition != null) {
            return evaluateCondition(itemData);
        }

        return true;
    }

    /**
     * Evaluate condition against item data
     * Supports simple conditions like "level=HIGH", "amount>1000"
     */
    private boolean evaluateCondition(Map<String, Object> itemData) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        // Simple equality condition: "level=HIGH"
        if (condition.contains("=")) {
            String[] parts = condition.split("=");
            if (parts.length == 2) {
                String field = parts[0].trim();
                String expectedValue = parts[1].trim();
                Object actualValue = itemData.get(field);
                return expectedValue.equals(String.valueOf(actualValue));
            }
        }

        // Future: Add support for >, <, >=, <=, etc.

        return true;
    }

    /**
     * Check if this rule has any mandatory roles
     */
    public boolean hasMandatoryRoles() {
        return roleRequirements.values().stream()
            .anyMatch(RoleRequirement::isMandatory);
    }

    /**
     * Check if this rule has only optional roles (OR logic - need at least one)
     */
    public boolean hasOnlyOptionalRoles() {
        return !hasMandatoryRoles() && !roleRequirements.isEmpty();
    }
}
