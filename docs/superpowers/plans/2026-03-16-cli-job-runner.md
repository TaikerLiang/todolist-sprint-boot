# CLI Job Runner Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `--command=<name>` CLI flag that runs a named maintenance job and exits, plus a `POST /admin/jobs/{name}` HTTP endpoint requiring `ROLE_ADMIN`.

**Architecture:** A `CliCommand` interface is implemented by `@Component` beans, auto-discovered by `CliCommandRunner` (an `ApplicationRunner`) which dispatches CLI invocations and exits cleanly. `JobController` exposes the same registry over HTTP. `HelloCommand` is the first job to validate the wiring.

**Tech Stack:** Spring Boot 3.1.5, Java 21, Lombok, Spring Security, JUnit 5, Mockito, MockMvc

---

## Chunk 1: Core Framework + HelloCommand + CliCommandRunner

### Task 1: `CliCommand` interface and `ExitExecutor`

**Files:**
- Create: `src/main/java/com/example/todolist/cli/CliCommand.java`
- Create: `src/main/java/com/example/todolist/cli/ExitExecutor.java`

- [ ] **Step 1: Create `CliCommand` interface**

```java
// src/main/java/com/example/todolist/cli/CliCommand.java
package com.example.todolist.cli;

import org.springframework.boot.ApplicationArguments;

public interface CliCommand {
    String getName();
    void execute(ApplicationArguments args) throws Exception;
}
```

- [ ] **Step 2: Create `ExitExecutor` functional interface**

This allows `CliCommandRunner` to be unit-tested without killing the JVM.

```java
// src/main/java/com/example/todolist/cli/ExitExecutor.java
package com.example.todolist.cli;

@FunctionalInterface
public interface ExitExecutor {
    void exit(int code);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/todolist/cli/
git commit -m "feat: add CliCommand interface and ExitExecutor"
```

---

### Task 2: `HelloCommand`

**Files:**
- Create: `src/main/java/com/example/todolist/cli/job/HelloCommand.java`
- Create: `src/test/java/com/example/todolist/cli/job/HelloCommandTest.java`

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/com/example/todolist/cli/job/HelloCommandTest.java
package com.example.todolist.cli.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatNoException;

class HelloCommandTest {

    private final HelloCommand command = new HelloCommand();

    @Test
    void getName_returnsHello() {
        assert command.getName().equals("hello");
    }

    @Test
    void execute_withName_doesNotThrow() throws Exception {
        var args = new DefaultApplicationArguments("--command=hello", "--name=Paul");
        assertThatNoException().isThrownBy(() -> command.execute(args));
    }

    @Test
    void execute_withoutName_doesNotThrow() throws Exception {
        var args = new DefaultApplicationArguments("--command=hello");
        assertThatNoException().isThrownBy(() -> command.execute(args));
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -Dtest=HelloCommandTest
```

Expected: compilation error — `HelloCommand` does not exist yet.

- [ ] **Step 3: Implement `HelloCommand`**

```java
// src/main/java/com/example/todolist/cli/job/HelloCommand.java
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
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./mvnw test -Dtest=HelloCommandTest
```

Expected: `BUILD SUCCESS`, 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/todolist/cli/job/HelloCommand.java \
        src/test/java/com/example/todolist/cli/job/HelloCommandTest.java
git commit -m "feat: add HelloCommand"
```

---

### Task 3: `CliCommandRunner`

**Files:**
- Create: `src/main/java/com/example/todolist/cli/CliCommandRunner.java`
- Create: `src/test/java/com/example/todolist/cli/CliCommandRunnerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/com/example/todolist/cli/CliCommandRunnerTest.java
package com.example.todolist.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CliCommandRunnerTest {

    private ExitExecutor exitExecutor;
    private CliCommand helloCommand;

    @BeforeEach
    void setUp() {
        exitExecutor = mock(ExitExecutor.class);
        helloCommand = mock(CliCommand.class);
        when(helloCommand.getName()).thenReturn("hello");
    }

    @Test
    void noCommandFlag_doesNotExit() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments(); // no --command

        runner.run(args);

        verifyNoInteractions(exitExecutor);
    }

    @Test
    void unknownCommand_exitsWithOne() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=unknown");

        runner.run(args);

        verify(exitExecutor).exit(1);
    }

    @Test
    void knownCommand_executesAndExitsWithZero() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=hello");

        runner.run(args);

        verify(helloCommand).execute(args);
        verify(exitExecutor).exit(0);
    }

    @Test
    void commandThrowsException_exitsWithOne() throws Exception {
        doThrow(new RuntimeException("boom")).when(helloCommand).execute(any());
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=hello");

        runner.run(args);

        verify(exitExecutor).exit(1);
    }

    @Test
    void duplicateCommandNames_throwsAtConstruction() {
        CliCommand duplicate = mock(CliCommand.class);
        when(duplicate.getName()).thenReturn("hello");

        assertThatThrownBy(() -> new CliCommandRunner(List.of(helloCommand, duplicate), exitExecutor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hello");
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -Dtest=CliCommandRunnerTest
```

Expected: compilation error — `CliCommandRunner` does not exist yet.

- [ ] **Step 3: Implement `CliCommandRunner`**

```java
// src/main/java/com/example/todolist/cli/CliCommandRunner.java
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
```

- [ ] **Step 4: Register `ExitExecutor` as a Spring bean**

The `ExitExecutor` bean owns the `SpringApplication.exit()` call — this keeps `CliCommandRunner.doExit()` simple and makes it fully unit-testable without a real `ApplicationContext`.

Open `src/main/java/com/example/todolist/TodolistApplication.java` and add:

```java
// src/main/java/com/example/todolist/TodolistApplication.java
package com.example.todolist;

import com.example.todolist.cli.ExitExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.ailef.snapadmin.external.SnapAdminAutoConfiguration;

@SpringBootApplication
@ImportAutoConfiguration(SnapAdminAutoConfiguration.class)
@EnableScheduling
public class TodolistApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodolistApplication.class, args);
    }

    @Bean
    public ExitExecutor exitExecutor(ApplicationContext ctx) {
        return code -> System.exit(SpringApplication.exit(ctx, () -> code));
    }
}
```

- [ ] **Step 5: Run tests — expect all pass**

```bash
./mvnw test -Dtest=CliCommandRunnerTest
```

Expected: `BUILD SUCCESS`, 5 tests pass.

- [ ] **Step 6: Run all tests to check nothing is broken**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/todolist/cli/CliCommandRunner.java \
        src/main/java/com/example/todolist/TodolistApplication.java \
        src/test/java/com/example/todolist/cli/CliCommandRunnerTest.java
git commit -m "feat: add CliCommandRunner with ExitExecutor"
```

---

## Chunk 2: JobController + Security Config

### Task 4: `JobController`

**Files:**
- Create: `src/main/java/com/example/todolist/controller/JobController.java`
- Create: `src/test/java/com/example/todolist/controller/JobControllerTest.java`

- [ ] **Step 1: Write failing tests**

Note: `JobController` keeps the `List<CliCommand>` and does a per-request stream lookup (no Map built at construction time). This lets `@MockBean CliCommand` work correctly — Spring injects all `CliCommand` beans into the list, and Mockito stubs are set up before each test.

```java
// src/test/java/com/example/todolist/controller/JobControllerTest.java
package com.example.todolist.controller;

import com.example.todolist.cli.CliCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tech.ailef.snapadmin.external.SnapAdminAutoConfiguration;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = JobController.class,
    excludeAutoConfiguration = {SnapAdminAutoConfiguration.class}
)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CliCommand helloCommand;  // Spring injects this as List<CliCommand>=[helloCommand]

    @MockBean
    private ApplicationArguments applicationArguments;  // required — not auto-provided in WebMvcTest slice

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_knownCommand_returns200() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");

        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isOk());

        verify(helloCommand).execute(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_unknownCommand_returns404() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");

        mockMvc.perform(post("/admin/jobs/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown command: unknown"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void runJob_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isForbidden());
    }

    @Test
    void runJob_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_commandThrowsException_returns500() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");
        doThrow(new RuntimeException("boom")).when(helloCommand).execute(any());

        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Command failed"));
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./mvnw test -Dtest=JobControllerTest
```

Expected: compilation error — `JobController` does not exist yet.

- [ ] **Step 3: Implement `JobController`**

```java
// src/main/java/com/example/todolist/controller/JobController.java
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
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
./mvnw test -Dtest=JobControllerTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/todolist/controller/JobController.java \
        src/test/java/com/example/todolist/controller/JobControllerTest.java
git commit -m "feat: add JobController for HTTP job triggering"
```

---

### Task 5: Security Config

**Files:**
- Modify: `src/main/java/com/example/todolist/config/SecurityConfig.java`

- [ ] **Step 1: Add `@EnableMethodSecurity` and update filter chain**

Open `src/main/java/com/example/todolist/config/SecurityConfig.java`.

Add `@EnableMethodSecurity` to the class and update the `authorizeHttpRequests` block. Replace the existing `permitAll()` group (lines 28–35) with:

```java
// Before (existing):
.authorizeHttpRequests(auth -> auth
        .requestMatchers(
                new AntPathRequestMatcher("/api/auth/login"),
                new AntPathRequestMatcher("/api/auth/refresh"),
                new AntPathRequestMatcher("/admin/**"),
                new AntPathRequestMatcher("/h2-console/**"),
                new AntPathRequestMatcher("/error")
        ).permitAll()
        .anyRequest().authenticated()
)

// After:
.authorizeHttpRequests(auth -> auth
        .requestMatchers(new AntPathRequestMatcher("/admin/jobs/**")).hasRole("ADMIN")
        .requestMatchers(
                new AntPathRequestMatcher("/api/auth/login"),
                new AntPathRequestMatcher("/api/auth/refresh"),
                new AntPathRequestMatcher("/admin/**"),
                new AntPathRequestMatcher("/h2-console/**"),
                new AntPathRequestMatcher("/error")
        ).permitAll()
        .anyRequest().authenticated()
)
```

Also add `@EnableMethodSecurity` annotation:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // add this
@RequiredArgsConstructor
public class SecurityConfig {
```

Add the import:
```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
```

- [ ] **Step 2: Run all tests**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/todolist/config/SecurityConfig.java
git commit -m "feat: enable method security and protect /admin/jobs/** with ROLE_ADMIN"
```

---

### Task 6: Manual smoke test (optional but recommended)

- [ ] **Step 1: Build the JAR**

```bash
./mvnw clean package -DskipTests
```

- [ ] **Step 2: Run the hello command**

```bash
java -jar target/todolist-*.jar --command=hello --name=World
```

Expected log output: `Hello World`, then clean shutdown with exit code 0.

- [ ] **Step 3: Run with unknown command**

```bash
java -jar target/todolist-*.jar --command=doesnotexist
echo "Exit code: $?"
```

Expected: error log `Unknown command: 'doesnotexist'`, exit code 1.

- [ ] **Step 4: Test HTTP endpoint (with app running normally)**

Start app: `./mvnw spring-boot:run`

```bash
# Unauthenticated — expect 403
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/admin/jobs/hello

# With valid ADMIN JWT — expect 200
curl -s -w "\n%{http_code}" -X POST http://localhost:8080/admin/jobs/hello \
  -H "Authorization: Bearer <admin-jwt>"

# Unknown job — expect 404
curl -s -w "\n%{http_code}" -X POST http://localhost:8080/admin/jobs/unknown \
  -H "Authorization: Bearer <admin-jwt>"
```
