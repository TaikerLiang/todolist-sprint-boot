package com.example.todolist.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CliCommandRunner implements ApplicationRunner {

    private final Map<String, CliCommand> commands;
    private final ExitExecutor exitExecutor;

    public CliCommandRunner(List<CliCommand> commandList, ExitExecutor exitExecutor) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(
                        CliCommand::getName,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate CLI command name: '" + a.getName() + "'");
                        }
                ));
        this.exitExecutor = exitExecutor;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("command")) {
            return;
        }

        String commandName = args.getOptionValues("command").get(0);
        CliCommand command = commands.get(commandName);

        if (command == null) {
            log.error("Unknown command: '{}'", commandName);
            exitExecutor.exit(1);
            return;
        }

        try {
            command.execute(args);
            exitExecutor.exit(0);
        } catch (Exception e) {
            log.error("Command '{}' failed: {}", commandName, e.getMessage(), e);
            exitExecutor.exit(1);
        }
    }
}
