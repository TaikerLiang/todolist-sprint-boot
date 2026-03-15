package com.example.todolist.cli;

import org.springframework.boot.ApplicationArguments;

public interface CliCommand {
    String getName();
    void execute(ApplicationArguments args) throws Exception;
}
