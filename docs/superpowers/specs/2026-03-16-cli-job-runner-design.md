# CLI Job Runner Design

**Date:** 2026-03-16
**Branch:** feat/taikerliang/cli/1

## Overview

A framework for running one-time maintenance jobs in the Spring Boot todolist service. Jobs can be triggered two ways:

1. **CLI** — run the JAR with `--command=<name> [--key=value ...]` flags; app exits after job completes
2. **HTTP** — `POST /admin/jobs/{name}` while the app is running; requires `ROLE_ADMIN`

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
- `execute` declares `throws Exception` to allow jobs to propagate any checked exception without wrapping
- **CLI path:** args contain the full `--key=value` options passed at startup
- **HTTP path:** args reflect JVM startup flags only (not per-request params) — commands must handle missing options via defaults. HTTP-triggered jobs are zero-argument invocations in this version.

### `CliCommandRunner`

A `@Component` `ApplicationRunner`:

**Constructor:** Eagerly builds the command registry via `Collectors.toMap` with a merge function that throws `IllegalStateException` on duplicate names. This runs at `ApplicationContext` initialisation — a duplicate name prevents **both** CLI runs and the web server from starting. This is intentional fail-fast behaviour to catch programmer errors at deploy time.

**`run()` method:**
1. Returns early (no-op) if `--command` option is absent — web app continues normally
2. Looks up the command by name; if not found: `log.error` + clean exit(1)
3. Calls `command.execute(args)` inside a `try/catch (Exception e)`
4. All exit paths (unknown command, exception, success) call `SpringApplication.exit(applicationContext, () -> exitCode)` then `System.exit(exitCode)` for a clean shutdown

**Testability note:** `System.exit()` terminates the test JVM. To enable unit testing, wrap exit calls in an injectable `ExitExecutor` functional interface (e.g., `(code) -> System.exit(code)`) that can be replaced with a no-op in tests.

### `HelloCommand` (first job)

A `@Component` implementing `CliCommand` to validate the wiring:

```bash
java -jar app.jar --command=hello --name=Paul
# log.info: Hello Paul

java -jar app.jar --command=hello
# log.info: Hello stranger
```

- `getName()` returns `"hello"`
- Uses `log.info` for output (per project logging standards)
- `--name` is optional; use `args.containsOption("name")` before `getOptionValues()` to avoid NPE; defaults to `"stranger"`
- No transaction needed — read-only greeting, no DB interaction

### `JobController`

A `@RestController` in `com.example.todolist.controller`. Spring calls it through its AOP proxy so `@PreAuthorize` is enforced correctly.

`ApplicationArguments` is injected via constructor — Spring Boot registers it as a singleton bean containing JVM startup flags only, never per-request parameters. This is by design: HTTP jobs are zero-argument in this version. Job implementations must not rely on `containsOption("command")` or other CLI-specific flags being present, as they will not be set during normal HTTP-triggered execution.

- `POST /admin/jobs/{name}` — triggers the named job
- Secured at two levels (intentional defence in depth):
  - Filter chain: `hasRole('ADMIN')` via `SecurityFilterChain`
  - Method: `@PreAuthorize("hasRole('ADMIN')")`
- Response is `application/json` (serialised by Jackson via `@RestController`)
- `200 OK` — empty body on success
- `404 Not Found` — `Map.of("error", "Unknown command: " + name)`
- `500 Internal Server Error` — `Map.of("error", "Command failed")` — no exception detail exposed; full stack trace logged server-side
- Jobs are not wrapped in a transaction by the framework — job implementations that modify data must annotate `execute()` with `@Transactional` or manage transactions explicitly
- No concurrency protection — concurrent calls run the job multiple times

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

**Filter chain:** **Replace** the existing `permitAll()` rule for `/admin/**` with the following two-rule block (order matters — Spring stops at the first match):

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(new AntPathRequestMatcher("/admin/jobs/**")).hasRole("ADMIN")  // add this BEFORE
    .requestMatchers(new AntPathRequestMatcher("/admin/**")).permitAll()             // keep existing SnapAdmin rule
    // ... remaining existing rules unchanged
)
```

**Method security:** Add `@EnableMethodSecurity` to the existing `SecurityConfig` — it is **not yet present**.

**CLI:** No auth required — requires server-level access to run the JAR.

## Argument Passing (CLI)

```bash
java -jar app.jar --command=hello --name=Paul
```

Access parameters safely:

```java
String name = args.containsOption("name")
        ? args.getOptionValues("name").get(0)
        : "stranger";
```

Always use `containsOption()` before `getOptionValues()`.

## Error Handling

| Scenario | CLI Behaviour | HTTP Behaviour |
|---|---|---|
| Unknown command | `log.error` + `SpringApplication.exit` + `System.exit(1)` | `404 {"error": "Unknown command: <name>"}` |
| Command throws exception | `log.error` + `SpringApplication.exit` + `System.exit(1)` | `500 {"error": "Command failed"}` |
| Missing optional argument | Command uses default value | Command uses default value |
| Duplicate command name | `IllegalStateException` at startup — **also prevents web server from starting**; this is intentional fail-fast behaviour to catch programmer errors at deploy time | Same |

## Shutdown (CLI)

All exit paths:

```java
int exitCode = SpringApplication.exit(applicationContext, () -> code);
System.exit(exitCode);
```

Inject `ApplicationContext` into `CliCommandRunner`.

## Future Jobs

Additional jobs: implement `CliCommand`, annotate `@Component`. Example:
- `cleanup-expired-tokens` — wraps `RefreshTokenService.cleanupExpiredTokens()`

HTTP parameter passing for future jobs (e.g., JSON body) is out of scope for this iteration.

## Constraints

- Java 21, Spring Boot 3.1.5
- Lombok: `@Slf4j`, `@RequiredArgsConstructor`, `@Component` per project standards
- No new dependencies required
- No schema changes required
