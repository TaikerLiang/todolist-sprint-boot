package com.example.todolist.cli.job;

import com.example.todolist.cli.CliCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelloCommand implements CliCommand {

    @Override
    public String getName() {
        return "hello";
    }

    @Override
    public void execute(ApplicationArguments args) {
        String name = args.containsOption("name")
                ? args.getOptionValues("name").get(0)
                : "stranger";
        log.info("Hello {}", name);
    }
}
