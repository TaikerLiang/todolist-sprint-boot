# CLI Job Runner Design

**Date:** 2026-03-16
**Branch:** feat/taikerliang/cli/1

## Overview

A framework for running one-time maintenance jobs in the Spring Boot todolist service. Jobs can be triggered two ways:

1. **CLI** — run the JAR with `--command=<name>` flags, app exits after job completes
2. **HTTP** — `POST /admin/jobs/{name}` while the app is running, requires `ROLE_ADMIN`

Both paths share the same job registry — no duplication of logic.

## Components

### `CliCommand` Interface

```java
public interface CliCommand {
    String getName();
    void execute(ApplicationArguments args) throws Exception;
}
```

- `getName()` returns the command identifier used for routing (e.g., `"hello"`)
- `execute(ApplicationArguments args)` receives the full Spring `ApplicationArguments`, enabling keyword argument access via `args.getOptionValues("key")`

### `CliCommandRunner`

An `ApplicationRunner` that:
1. Returns early (no-op) if `--command` option is absent — normal web app startup proceeds
2. Looks up the command by name from the auto-discovered registry
3. Calls `command.execute(args)` and exits with code `0` on success, `1` on failure
4. Logs errors with `log.error` (Slf4j); uses `System.exit` for shell-visible exit codes

Commands are auto-registered: all Spring beans implementing `CliCommand` are injected as a `List<CliCommand>` and mapped by `getName()`. No manual registration required.

### `HelloCommand` (first job)

A minimal command to validate the wiring:

```bash
java -jar app.jar --command=hello --name=Paul
# logs: Hello Paul

java -jar app.jar --command=hello
# logs: Hello stranger
```

- `getName()` returns `"hello"`
- `--name` is optional; defaults to `"stranger"` if absent

### `JobController`

REST controller at `/admin/jobs/{name}`:

- `POST /admin/jobs/{name}` — triggers the named job
- Requires `ROLE_ADMIN` via `@PreAuthorize("hasRole('ADMIN')")`
- Returns `200 OK` on success, `404` for unknown command, `500` on failure
- Injects `Map<String, CliCommand>` built from the same registry

## Package Structure

```
com.example.todolist/
├── cli/
│   ├── CliCommand.java
│   ├── CliCommandRunner.java
│   └── job/
│       └── HelloCommand.java
└── controller/
    └── JobController.java
```

## Security

- **CLI:** No auth required — access requires server-level access to run the JAR
- **HTTP:** JWT-authenticated, `ROLE_ADMIN` required via `@PreAuthorize`
- `@EnableMethodSecurity` must be present on `SecurityConfig` (or a config class)
- `/admin/jobs/**` must not be blocked at the `SecurityFilterChain` level (permitAll at filter chain, method-level security handles the role check)

## Argument Passing (CLI)

Spring's `ApplicationArguments` natively parses `--key=value` options:

```bash
java -jar app.jar --command=hello --name=Paul
```

Commands access parameters via `args.getOptionValues("name").get(0)`. Each command is responsible for its own argument validation and defaults.

## Error Handling

| Scenario | CLI Behaviour | HTTP Behaviour |
|---|---|---|
| Unknown command | `log.error` + `System.exit(1)` | `404 Not Found` |
| Command throws exception | `log.error` + `System.exit(1)` | `500 Internal Server Error` |
| Missing required argument | Command-level validation | Command-level validation |

## Future Jobs

Once the framework is in place, additional jobs follow the same pattern — implement `CliCommand`, annotate with `@Component`. Example candidates:
- `cleanup-expired-tokens` — wraps `RefreshTokenService.cleanupExpiredTokens()`

## Constraints

- Java 21, Spring Boot 3.1.5
- Lombok: use `@Slf4j`, `@RequiredArgsConstructor`, `@Component` per project standards
- No new dependencies required
- Hibernate DDL auto is `none` — this feature requires no schema changes
