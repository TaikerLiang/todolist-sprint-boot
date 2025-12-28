package com.example.todolist.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoleRequirement {
    private boolean mandatory;

    public static RoleRequirement mandatory() {
        return new RoleRequirement(true);
    }

    public static RoleRequirement optional() {
        return new RoleRequirement(false);
    }
}
