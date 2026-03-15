package com.example.todolist.cli;

@FunctionalInterface
public interface ExitExecutor {
    void exit(int code);
}
