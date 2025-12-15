<!--
Sync Impact Report:
Version change: INITIAL → 1.0.0
New constitution established with Spring Boot-specific principles
Modified principles: N/A (initial version)
Added sections:
  - I. Layered Architecture
  - II. UTC-First Timestamps
  - III. Database Migration Discipline
  - IV. RESTful API Standards
  - V. Comprehensive Testing
  - VI. Build & Dependency Management
  - Technology Standards
  - Development Workflow
Removed sections: N/A (initial version)
Templates requiring updates:
  ✅ plan-template.md - Constitution Check section ready for validation
  ✅ spec-template.md - User stories and requirements align with API and testing standards
  ✅ tasks-template.md - Task organization supports layered architecture and test-first approach
  ✅ agent-file-template.md - Ready to extract Spring Boot conventions
  ✅ checklist-template.md - Ready for feature-specific validation
Follow-up TODOs: None
-->

# Todo List API Constitution

## Core Principles

### I. Layered Architecture (NON-NEGOTIABLE)

The application MUST follow Spring Boot's standard layered architecture with clear separation of concerns:
- **Model Layer**: JPA entities representing database tables only; no business logic
- **Repository Layer**: Spring Data JPA repositories for data access; extends JpaRepository
- **Service Layer**: Business logic and orchestration; never expose repositories to controllers
- **Controller Layer**: REST endpoints only; thin controllers that delegate to services

**Rationale**: This separation enables independent testing of each layer, prevents tight coupling, and maintains single responsibility. Business logic in services can be reused across multiple controllers and tested without HTTP concerns.

### II. UTC-First Timestamps (NON-NEGOTIABLE)

All timestamp fields MUST use `java.time.Instant` (UTC) rather than `LocalDateTime`:
- Database timezone configured to UTC in `application.properties`
- JPA entities use `Instant` for all temporal fields
- NO usage of `LocalDateTime` for persistence
- Client applications handle timezone conversion for display

**Rationale**: UTC storage prevents timezone ambiguity, eliminates daylight saving time bugs, and ensures consistent timestamp comparison across distributed systems. LocalDateTime creates implicit timezone assumptions that cause production incidents.

### III. Database Migration Discipline (NON-NEGOTIABLE)

All schema changes MUST go through Liquibase migrations:
- Hibernate `ddl-auto` set to `none` (never `update` or `create-drop` in production config)
- Changes to JPA entities followed by `./mvnw liquibase:diff` to generate migration
- Manual review of generated migrations before applying
- NO manual SQL execution outside of migration files
- Rollback procedures tested before production deployment

**Rationale**: Automated schema generation causes irreversible data loss, lacks audit trail, and breaks in team environments. Liquibase provides versioned, reviewable, and reversible schema changes with automatic rollback capability.

### IV. RESTful API Standards

REST endpoints MUST follow these conventions:
- Resource-based URLs (nouns, not verbs): `/api/todos`, not `/api/getTodos`
- Proper HTTP methods: GET (read), POST (create), PUT (update), DELETE (remove)
- HTTP status codes: 200 (success), 201 (created), 204 (no content), 400 (bad request), 404 (not found), 500 (server error)
- JSON request/response bodies with consistent naming (snake_case in database, camelCase in API)
- `@RestController` for controllers; `@RequestMapping` for base paths

**Rationale**: RESTful conventions create predictable APIs that follow industry standards, reducing integration effort and enabling automatic client generation. Consistent patterns reduce cognitive load for API consumers.

### V. Comprehensive Testing

Testing strategy MUST include:
- **Unit Tests**: Service layer logic with mocked dependencies
- **Integration Tests**: Repository layer with test database (H2 or TestContainers)
- **API Tests**: Controller endpoints with MockMvc or WebTestClient
- Tests run via `./mvnw test` before every commit
- Test coverage for happy path AND edge cases (null inputs, invalid data, constraint violations)

Tests are OPTIONAL per feature unless explicitly requested, but when included MUST follow Test-First approach:
- Write tests FIRST
- Verify tests FAIL before implementation
- Implement until tests pass
- Red-Green-Refactor cycle

**Rationale**: Comprehensive testing prevents regression, documents expected behavior, and enables confident refactoring. Test-first approach ensures tests actually validate requirements rather than retrofitting to pass existing code.

### VI. Build & Dependency Management

Maven configuration MUST enforce:
- Java 21 source/target in compiler plugin
- Explicit dependency versions (avoid version ranges)
- Lombok annotation processing configured in maven-compiler-plugin
- No SNAPSHOT dependencies in production builds
- Reproducible builds: `./mvnw clean package` always produces same artifact for same source

**Rationale**: Explicit versions prevent surprise breakages from transitive dependency updates. Proper Lombok setup prevents compilation issues. Reproducible builds enable reliable deployments and rollback.

## Technology Standards

**Language/Framework**: Java 21, Spring Boot 3.1.5+
**Database**: PostgreSQL with Liquibase migrations
**ORM**: Spring Data JPA with Hibernate 6
**Build Tool**: Maven 3.9.11+
**Naming Convention**: snake_case for database columns (via CamelCaseToUnderscoresNamingStrategy), camelCase for Java fields
**Boilerplate Reduction**: Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor` on entities)
**Logging**: Structured JSON logging with Logstash encoder

## Development Workflow

### Code Organization
- Package by feature when application grows beyond basic CRUD
- Entity classes in `com.example.todolist.model`
- Keep transaction boundaries at service layer (`@Transactional` on service methods, not repositories)
- Avoid circular dependencies between services

### Database Workflow
1. Modify JPA entity classes
2. Run `./mvnw liquibase:diff` to generate changelog
3. Review generated `db/changelog/generated-changelog.yaml`
4. Run `./mvnw liquibase:update` to apply (or use Makefile: `make migrate`)
5. Test migration with `make rollback` and re-apply

### Environment Configuration
- Use environment variables for database connection (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`)
- NO hardcoded credentials in `application.properties`
- Provide `.env.example` for required variables

### API Development
- Define request/response DTOs for complex operations (avoid exposing entities directly)
- Validate input with `@Valid` and `@NotNull` annotations
- Return proper HTTP status codes and error responses
- Log requests/responses at DEBUG level

## Governance

### Amendment Process
1. Propose change with rationale in pull request
2. Update affected templates (plan, spec, tasks) for consistency
3. Increment version number according to semantic versioning:
   - MAJOR: Removing/replacing core principles (e.g., switching from Liquibase to Flyway)
   - MINOR: Adding new principle or expanding section (e.g., adding security standards)
   - PATCH: Clarifications, typo fixes, non-semantic improvements
4. Obtain approval from project maintainers
5. Update constitution.md and propagate to dependent files

### Compliance Verification
- All pull requests MUST reference constitution principles for architectural decisions
- New features MUST complete "Constitution Check" in plan.md before implementation
- Violations of NON-NEGOTIABLE principles require explicit justification and approval
- Code reviews verify adherence to layered architecture and testing standards

### Complexity Justification
Any deviation from simplicity principles (e.g., introducing repository pattern beyond JpaRepository, adding caching layer, implementing event sourcing) MUST:
- Document the specific problem being solved
- Explain why simpler alternatives are insufficient
- Track in plan.md Complexity Tracking section

**Version**: 1.0.0 | **Ratified**: 2025-12-16 | **Last Amended**: 2025-12-16
