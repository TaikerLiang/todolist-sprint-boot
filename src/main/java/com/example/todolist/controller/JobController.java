package com.example.todolist.controller;

import com.example.todolist.cli.CliCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jobs")
@Slf4j
@RequiredArgsConstructor
public class JobController {

    private final List<CliCommand> commands;
    private final ApplicationArguments applicationArguments;

    @PostMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> runJob(@PathVariable String name) {
        CliCommand command = commands.stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElse(null);

        if (command == null) {
            log.warn("HTTP job request for unknown command: '{}'", name);
            return ResponseEntity.status(404).body(Map.of("error", "Unknown command: " + name));
        }

        try {
            log.info("Executing job '{}' via HTTP", name);
            command.execute(applicationArguments);
            log.info("Job '{}' completed successfully", name);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Job '{}' failed: {}", name, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Command failed"));
        }
    }
}
