# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.1.5 todo list application with the following technology stack:

### Core Framework
- **Spring Boot**: 3.1.5
- **Java**: 21 (source/target compatibility)
- **Maven**: Build and dependency management

### Database & Persistence
- **PostgreSQL**: Primary production database
- **H2**: In-memory database for testing
- **Spring Data JPA**: Data access with Hibernate
- **Liquibase**: 4.27.0 - Database migration management

### Developer Tools & Libraries
- **Lombok**: 1.18.36 - Reduces boilerplate code with annotations
- **Spring DevTools**: Hot reload during development
- **SnapAdmin**: 0.2.1 - Auto-generated admin UI at `/admin`
- **Logstash Logback Encoder**: 7.4 - Structured logging

## Architecture

The application follows a standard Spring Boot layered architecture:

### Model Layer (`com.example.todolist.model`)
JPA entities representing database tables:

**Entities:**
- `Todo`: Task management with title, description, completed status, priority level, and user association
- `User`: User accounts with username, role, and todo list relationship
- `Invoice`: Billing records with UUID, amount, status, level, and user association

**Enums:**
- `Level`: Priority levels (LOW, MEDIUM, HIGH) - used by both Todo and Invoice
- `Role`: User roles for access control
- `InvoiceStatus`: Invoice lifecycle states (CREATED, PAID, CANCELLED, etc.)

**Design Patterns:**
- Uses `Instant` for timestamps (UTC-safe) rather than `LocalDateTime`
- Entities use Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`)
- Configured with `@Table` and `@Column` annotations with appropriate constraints
- FetchType.LAZY for relationships to optimize performance
- `@PreUpdate` lifecycle hooks (e.g., Invoice.updatedAt)

### Repository Layer (`com.example.todolist.repository`)
Spring Data JPA repositories extending `JpaRepository` for CRUD operations:
- `TodoRepository`
- `UserRepository`

### Service Layer (`com.example.todolist.service`)
Business logic layer:
- `TodoService`: Todo management operations
- `UserService`: User management operations

### Controller Layer (`com.example.todolist.controller`)
REST API endpoints using `@RestController`:
- `TodoController`: Todo CRUD endpoints
- `UserController`: User CRUD endpoints

## Database Configuration

### Environment Variables
The application uses PostgreSQL and requires these environment variables (stored in `.env` file):

```bash
DB_HOST=localhost           # Database host
DB_PORT=5432               # Database port (defaults to 5432)
DB_NAME=demo               # Database name (defaults to "demo")
DB_USER=default_user       # Database username (defaults to "default_user")
DB_PASSWORD=default_pass   # Database password (defaults to "default_pass")
DB_URL=jdbc:postgresql://localhost:5432/demo  # Full JDBC URL (for Liquibase)
```

### Hibernate Configuration
**CRITICAL**: Hibernate is configured with `ddl-auto: none` to prevent automatic schema changes. This means:
- Hibernate will NEVER automatically create or modify database tables
- All schema changes MUST be done through Liquibase migrations
- You must explicitly generate migrations after modifying JPA entities

### Additional Settings
- **Naming Strategy**: `CamelCaseToUnderscoresNamingStrategy` - Java camelCase fields map to snake_case columns
- **Time Zone**: UTC (configured via `hibernate.jdbc.time_zone`)
- **SQL Logging**: Enabled with formatting (`show-sql: true`, `format_sql: true`)
- **Open-in-View**: Enabled (required for SnapAdmin)

## Build and Run Commands

### Application Management

**Build the application:**
```bash
./mvnw clean package
```

**Run the application (default port 8080):**
```bash
./mvnw spring-boot:run
# Or use Makefile
make run
```

**Run on custom port:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
# Or use Makefile
make run PORT=9090
```

**Access Points:**
- API: `http://localhost:8080`
- SnapAdmin UI: `http://localhost:8080/admin`

### Testing

**Run all tests:**
```bash
./mvnw test
```

**Run a single test class:**
```bash
./mvnw test -Dtest=TodolistApplicationTests
```

**Run a specific test method:**
```bash
./mvnw test -Dtest=ClassName#methodName
```

## Database Migration with Liquibase

This project uses **Liquibase 4.27.0** with a Django-style Makefile wrapper for managing database migrations.

### Migration Workflow Overview

The migration system uses **diffChangeLog** to automatically generate migrations by comparing:
- **Source**: JPA entity models in `com.example.todolist.model` (Hibernate schema)
- **Target**: Actual PostgreSQL database schema

This ensures migrations reflect only the actual differences between your code and database.

### Prerequisites

1. **Environment Configuration** - Create a `.env` file in the project root:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=demo
DB_USER=default_user
DB_PASSWORD=default_pass
DB_URL=jdbc:postgresql://localhost:5432/demo
```

2. **Database Running** - Ensure PostgreSQL is running and accessible

3. **Changelog Structure**:
```
src/main/resources/db/changelog/
├── db.changelog-master.yaml          # Master changelog (includes all changes)
└── changes/
    ├── 0001-init.yaml               # Initial schema
    ├── 0002-rename-createdAt-to-created_at.yaml
    ├── 0003-create-users-table.yaml
    ├── 0004-add-user-to-todos.yaml
    ├── 0005_add_level_to_todo.yaml
    └── 0006_create_invoices_table.yaml
```

### Generating Migrations

**IMPORTANT**: Only generate migrations AFTER modifying JPA entities in `com.example.todolist.model`.

**Recommended: Using Makefile (auto-numbered)**
```bash
# Auto-numbered migration (0007, 0008, etc.)
make makemigration

# With descriptive name
make makemigration NAME=add_status_to_todo
```

This will:
1. Auto-detect the next migration number (e.g., 0007)
2. Run `liquibase:diff` to compare Hibernate models vs database
3. Generate `src/main/resources/db/changelog/changes/0007_add_status_to_todo.yaml`
4. Automatically append a tag changeset for versioning

**Alternative: Using Maven directly**
```bash
./mvnw liquibase:diff
```

### Applying Migrations

**Recommended: Using Makefile**
```bash
# Apply all pending migrations
make migrate

# Apply only the next pending migration (safe, incremental)
make migrate-one

# Migrate to a specific version (using tag)
make migrate-to NUM=0008
```

**Alternative: Using Maven directly**
```bash
./mvnw liquibase:update
```

### Checking Migration Status

```bash
# View pending migrations
make showmigrations

# Or using Maven
./mvnw liquibase:status
```

### Rollback Operations

**Roll back migrations:**
```bash
# Rollback 1 changeset (default)
make rollback

# Rollback N changesets
make rollback COUNT=3
```

**Preview rollback (safe - doesn't execute):**
```bash
# Preview what SQL would be executed
make rollback-preview COUNT=1

# Output saved to: target/liquibase/migrate.sql
```

### Fake Migrations (Mark as Executed)

Useful when you need to sync the changelog status without actually running migrations (e.g., when manually applying changes or syncing environments).

```bash
# Mark all pending migrations as executed (without running them)
make fake-migrate

# Mark migrations up to a specific version as executed
make fake-migrate-to NUM=0008

# Preview what would be marked
make fake-migrate-preview
```

### Migration Configuration

The Liquibase Maven plugin is configured in `pom.xml` with:

**Database Connection:**
- `url`: `${env.DB_URL}` - PostgreSQL JDBC URL
- `username`: `${env.DB_USER}`
- `password`: `${env.DB_PASSWORD}`

**Hibernate Reference (for diff generation):**
- `referenceUrl`: `hibernate:spring:com.example.todolist.model?dialect=org.hibernate.dialect.PostgreSQLDialect`
- Package: All entities in `com.example.todolist.model`

**Dependencies:**
- `liquibase-hibernate6`: 4.27.0 - Hibernate 6 integration
- Spring ORM & Context - Required for entity scanning
- Jakarta Validation & Hibernate Validator - For constraint annotations
- PostgreSQL JDBC Driver - For database connectivity

### Best Practices

1. **Always generate migrations after entity changes** - Never modify database schema manually
2. **Use descriptive migration names** - e.g., `make makemigration NAME=add_email_to_user`
3. **Review generated migrations** - Check the YAML file before applying
4. **Test migrations** - Use `migrate-one` to apply incrementally and test each change
5. **Use tags for versioning** - Each migration automatically gets a tag for easy rollback
6. **Keep `.env` file secure** - Don't commit it to version control (already in `.gitignore`)

### Troubleshooting

**"No changes found"**: Your JPA entities match the database - no migration needed

**Connection errors**: Check your `.env` file and ensure PostgreSQL is running

**Conflicts**: If migration fails, use `rollback` to undo, fix the issue, and regenerate

**Manual schema changes**: Use `fake-migrate` to sync the changelog status if you manually modified the database

## Additional Features

### SnapAdmin - Auto-Generated Admin UI

The project includes **SnapAdmin 0.2.1**, which provides an automatic admin interface for all JPA entities.

**Configuration** (`application.yml`):
```yaml
snapadmin:
  enabled: true
  baseUrl: admin
  modelsPackage: com.example.todolist.model
```

**Access**: `http://localhost:8080/admin`

**Features**:
- Automatic CRUD interface for Todo, User, and Invoice entities
- Browse, create, edit, and delete records
- No additional code required - automatically scans `com.example.todolist.model`

**Note**: Requires `spring.jpa.open-in-view: true` to be enabled

### Lombok Integration

The project uses **Lombok 1.18.36** to reduce boilerplate code and enforce consistency.

**Standard Annotations (Use by Default)**:

| Annotation | Usage | Purpose |
|------------|-------|---------|
| `@Getter` / `@Setter` | JPA Entities | Generate getters/setters |
| `@NoArgsConstructor` | JPA Entities | No-arg constructor (required by JPA) |
| `@RequiredArgsConstructor` | Services, Controllers, Components | Constructor for `final` fields (dependency injection) |
| `@Slf4j` | All classes needing logging | Generate logger field |

**Standard Class Templates**:

**Entity:**
```java
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MyEntity { ... }
```

**Service:**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class MyService {
    private final MyRepository repository;
    ...
}
```

**Controller:**
```java
@RestController
@Slf4j
@RequiredArgsConstructor
public class MyController {
    private final MyService service;
    ...
}
```

**IDE Setup**: Ensure Lombok plugin is installed in your IDE and annotation processing is enabled.

**Maven Configuration**: Lombok is configured as an annotation processor path in the compiler plugin.

**Important**: See "Coding Standards" section above for detailed examples and mandatory patterns.

## Coding Standards

### Lombok Annotations (MANDATORY)

**Always use the following Lombok annotations for consistency and clean code:**

#### Constructor Patterns

**For JPA Entities:**
```java
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor  // Required by JPA
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    // Custom constructor for business logic
    public User(String username) {
        this.username = username;
    }
}
```

**For Services (Dependency Injection):**
```java
@Service
@Slf4j
@RequiredArgsConstructor  // Generates constructor for final fields
public class UserService {
    private final UserRepository userRepository;
    private final TodoRepository todoRepository;

    // No need to write constructor - Lombok generates it!

    public User createUser(String username) {
        log.info("Creating user: {}", username);
        return userRepository.save(new User(username));
    }
}
```

**For Controllers:**
```java
@RestController
@RequestMapping("/api/users")
@Slf4j
@RequiredArgsConstructor  // Generates constructor for final fields
public class UserController {
    private final UserService userService;

    @PostMapping
    public User createUser(@RequestBody CreateUserRequest request) {
        log.info("Received request to create user: {}", request.getUsername());
        return userService.createUser(request.getUsername());
    }
}
```

**Rules**:
- ✅ Use `@NoArgsConstructor` for JPA entities (required by JPA specification)
- ✅ Use `@RequiredArgsConstructor` for Services, Controllers, and Components (dependency injection)
- ✅ Declare dependencies as `private final` fields when using `@RequiredArgsConstructor`
- ✅ Use `@Getter` and `@Setter` on entities to reduce boilerplate
- ❌ Never write manual constructors when Lombok can generate them
- ❌ Never use field injection (`@Autowired` on fields) - use constructor injection instead

### Logging (MANDATORY)

**Always use Slf4j with Lombok's @Slf4j annotation for logging.**

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class MyService {
    private final MyRepository myRepository;

    public void doSomething() {
        log.info("Doing something important");
        log.debug("Debug details: {}", details);
        log.error("Error occurred", exception);
    }
}
```

**Rules**:
- ✅ Use `@Slf4j` annotation on all classes that need logging
- ✅ Use parameterized logging: `log.info("User {}", username)`
- ❌ Never use `System.out.println()` or `System.err.println()`
- ❌ Never use string concatenation in log messages
- ❌ Never use other logging frameworks directly

See the "Application Logging" section below for detailed examples.

## Development Notes

### Java Version
- **Java 21** is required (source/target compatibility)
- Ensure `JAVA_HOME` points to JDK 21
- Maven wrapper (`./mvnw`) handles Maven automatically

### Timestamp Handling
- **Always use `Instant`** (UTC-based) rather than `LocalDateTime` to avoid timezone issues
- JPA timezone is explicitly configured to UTC (`hibernate.jdbc.time_zone: UTC`)
- Database columns use `TIMESTAMP WITH TIME ZONE`
- Example: `private Instant createdAt = Instant.now();`

### Database Schema Management
- **Hibernate DDL Auto**: Set to `none` - Hibernate will NEVER modify schema
- **All schema changes**: Must go through Liquibase migrations
- **Naming Convention**: Automatic snake_case conversion (camelCase → snake_case)

### Application Logging

**Use Slf4j for all logging** - This is a MANDATORY standard for this project.

**Implementation**:
- Use Lombok's `@Slf4j` annotation on classes that need logging
- This automatically provides a `log` field without boilerplate code
- **DO NOT** use `System.out.println()` or other logging frameworks

**Example**:
```java
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TodoService {
    public Todo createTodo(Todo todo) {
        log.info("Creating new todo: {}", todo.getTitle());
        log.debug("Todo details: {}", todo);
        try {
            return todoRepository.save(todo);
        } catch (Exception e) {
            log.error("Failed to create todo: {}", todo.getTitle(), e);
            throw e;
        }
    }
}
```

**Log Levels**:
- `log.error()` - Errors and exceptions
- `log.warn()` - Warnings and potential issues
- `log.info()` - Important business events and state changes
- `log.debug()` - Detailed debugging information
- `log.trace()` - Very detailed diagnostic information

**Structured Logging**:
- The project includes Logstash Logback Encoder for JSON-formatted logs
- Use parameterized logging: `log.info("User {} created todo {}", userId, todoId)`
- Never use string concatenation: ❌ `log.info("User " + userId + " created")`

### SQL Logging
- SQL queries are logged and formatted in console (see `application.yml`)
- `show-sql: true` - Shows executed SQL
- `format_sql: true` - Formats SQL for readability
- Useful for debugging query performance and understanding Hibernate behavior

### Entity Design Patterns
- **Lombok Annotations**: Always use `@Getter`, `@Setter`, and `@NoArgsConstructor` on entities
- **Constructor Injection**: Services and Controllers use `@RequiredArgsConstructor` with `final` fields
- **No Field Injection**: Never use `@Autowired` on fields - use constructor injection via `@RequiredArgsConstructor`
- **Relationships**: Use `FetchType.LAZY` to avoid N+1 query problems
- **Cascade Types**: Carefully configure cascade operations (e.g., `CascadeType.ALL`)
- **Bidirectional Relationships**: Use `@JsonIgnore` to prevent circular serialization
- **Lifecycle Hooks**: Use `@PreUpdate`, `@PrePersist` for automatic timestamp updates

### Project Structure
```
todolist/
├── src/main/java/com/example/todolist/
│   ├── TodolistApplication.java      # Main Spring Boot application
│   ├── model/                         # JPA entities (Todo, User, Invoice, enums)
│   ├── repository/                    # Spring Data repositories
│   ├── service/                       # Business logic layer
│   └── controller/                    # REST API controllers
├── src/main/resources/
│   ├── application.yml                # Application configuration
│   └── db/changelog/                  # Liquibase migration files
├── .env                               # Database credentials (not in git)
├── Makefile                           # Django-style migration commands
├── pom.xml                            # Maven dependencies
└── CLAUDE.md                          # This file
```

### Getting Help
- **Makefile Commands**: Run `make help` to see all available commands
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **Liquibase Docs**: https://docs.liquibase.com
- **SnapAdmin**: https://github.com/aileftech/snap-admin

## Common Workflows

### Adding a New Entity

1. **Create the JPA entity** in `src/main/java/com/example/todolist/model/`
   ```java
   @Entity
   @Table(name = "table_name")
   @Getter
   @Setter
   @NoArgsConstructor
   public class MyEntity {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       // Add fields with annotations
   }
   ```
2. **Create repository** in `repository/` package
   ```java
   public interface MyEntityRepository extends JpaRepository<MyEntity, Long> {
   }
   ```
3. **Create service** (optional) in `service/` package
   ```java
   @Service
   @Slf4j
   @RequiredArgsConstructor
   public class MyEntityService {
       private final MyEntityRepository repository;

       public MyEntity create(MyEntity entity) {
           log.info("Creating entity: {}", entity);
           return repository.save(entity);
       }
   }
   ```
4. **Generate migration**: `make makemigration NAME=create_entity_name`
5. **Review the migration** in `src/main/resources/db/changelog/changes/`
6. **Apply migration**: `make migrate`
7. **Restart application** - SnapAdmin will automatically include the new entity

### Modifying an Existing Entity

1. **Update the JPA entity** (add/remove/modify fields)
2. **Generate migration**: `make makemigration NAME=modify_entity_name`
3. **Review the migration** - Ensure it matches your intent
4. **Apply migration**: `make migrate`
5. **Test the changes**

### Creating a New Feature

1. **Plan the data model** - What entities/fields are needed?
2. **Create/modify entities** with proper annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`)
3. **Generate and apply migrations** (`make makemigration NAME=...`, `make migrate`)
4. **Create repository** extending `JpaRepository` (if custom queries needed)
5. **Implement service layer** with `@Service`, `@Slf4j`, `@RequiredArgsConstructor`
   - Use `private final` fields for repository dependencies
   - Add logging for important operations
6. **Create REST controller** with `@RestController`, `@Slf4j`, `@RequiredArgsConstructor`
   - Use `private final` fields for service dependencies
   - Add logging for incoming requests
7. **Test the API** using SnapAdmin or API client

## Environment Setup Checklist

- [ ] Java 21 installed (`java -version`)
- [ ] PostgreSQL running
- [ ] `.env` file created with database credentials
- [ ] Dependencies installed (`./mvnw clean install`)
- [ ] Database schema migrated (`make migrate`)
- [ ] Application runs (`make run`)
- [ ] SnapAdmin accessible at `http://localhost:8080/admin`

## Admin Audit Logging

The application includes comprehensive audit logging for all create, update, and delete operations on Todo and Invoice entities. Administrators can track who made changes, when they occurred, and what specifically changed (before/after field values).

### Features

- **Complete Change History**: View all CREATE, UPDATE, DELETE operations for any item
- **Field-Level Tracking**: See exactly what changed (before/after values for each field)
- **Request Correlation**: Group related changes by request ID (transaction tracking)
- **Search & Filter**: Find changes by user, date range, entity type, or operation
- **Indefinite Retention**: Audit logs preserved even after entity deletion

### Implementation Details

**Audit Infrastructure:**
- **Model**: `UserActionLogs` entity with JSONB field changes (table: `admin_portal_user_action_logs`)
- **Repository**: Custom repository pattern with JPQL queries for flexible searching
- **Service**: `AuditCaptureService` automatically captures changes via service layer integration
- **Request Context**: ThreadLocal-based request ID and user ID tracking

**Automatic Audit Capture:**
- All Todo and Invoice CUD operations automatically generate audit logs
- Changes tracked via `TodoService` and `InvoiceService` integration
- Transactional safety: audit write failures roll back business operations

### REST API Endpoints

All endpoints require `ROLE_ADMIN` for access.

**Get Audit History for Specific Item:**
```bash
# Get complete change history for Todo ID 123
GET /api/audit/todos/123/history

# Get complete change history for Invoice ID 456
GET /api/audit/invoices/456/history
```

**Search and Filter Audit Logs:**
```bash
# Find all changes by specific user
GET /api/audit/search?username=john@example.com

# Find all changes in date range
GET /api/audit/search?startDate=2026-02-01T00:00:00Z&endDate=2026-02-11T23:59:59Z

# Find all Todo deletions
GET /api/audit/search?entityType=Todo&operation=DELETE

# Combine filters with pagination
GET /api/audit/search?entityType=Invoice&username=admin@example.com&page=0&size=20
```

**Correlate Related Changes:**
```bash
# Get all changes in a single request/transaction
GET /api/audit/revisions/{requestId}
```

### Example Response

```json
{
  "entityId": "123",
  "entityType": "Todo",
  "totalEntries": 3,
  "history": [
    {
      "id": "a7f3e4d2-1b9c-4e5a-8f2d-1c3b4a5e6f7a",
      "timestamp": "2026-02-11T15:30:00Z",
      "operation": "DELETE",
      "entityType": "Todo",
      "entityId": 123,
      "username": "admin@example.com",
      "requestId": "b8e4f5c3-2c0d-5f6b-9e3f-2d4c5b6e7f8b",
      "changedFields": null
    },
    {
      "id": "c8f5g6d4-3d1e-6g7c-0f4g-3e5d6c7f8g9c",
      "timestamp": "2026-02-11T15:15:00Z",
      "operation": "UPDATE",
      "entityType": "Todo",
      "entityId": 123,
      "username": "john@example.com",
      "requestId": "d9g6h7e5-4e2f-7h8d-1g5h-4f6e7d8g9h0d",
      "changedFields": [
        {
          "fieldName": "title",
          "oldValue": "Original Title",
          "newValue": "Updated Title",
          "fieldType": "String"
        },
        {
          "fieldName": "completed",
          "oldValue": false,
          "newValue": true,
          "fieldType": "Boolean"
        }
      ]
    },
    {
      "id": "e0h7i8f6-5f3g-8i9e-2h6i-5g7f8e9h0i1e",
      "timestamp": "2026-02-11T14:00:00Z",
      "operation": "CREATE",
      "entityType": "Todo",
      "entityId": 123,
      "username": "john@example.com",
      "requestId": "f1i8j9g7-6g4h-9j0f-3i7j-6h8g9f0i1j2f",
      "changedFields": null
    }
  ]
}
```

### Request Correlation

All changes within a single HTTP request share the same `requestId` for transaction-level tracking:

**Example Scenario:**
```java
// User updates multiple Todos in a bulk operation
// All updates will have the same requestId for correlation
```

**Correlation Endpoint:**
```bash
GET /api/audit/revisions/b8e4f5c3-2c0d-5f6b-9e3f-2d4c5b6e7f8b
```

Returns all changes (across different entities) that occurred in the same request.

### Database Schema

**Table**: `admin_portal_user_action_logs`

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Primary key (auto-generated) |
| `entity_type` | VARCHAR(50) | Entity type (e.g., "Todo", "Invoice") |
| `entity_id` | BIGINT | ID of affected entity |
| `operation` | VARCHAR(10) | Operation type (CREATE/UPDATE/DELETE) |
| `created_by` | VARCHAR(100) | Username who made the change |
| `created_at` | TIMESTAMP | When change occurred (UTC) |
| `changes` | JSONB | Field changes (before/after values) |
| `request_id` | UUID | Request correlation ID |

**Indexes**: entity_type, request_id, created_by, created_at (for efficient querying)

### Implementation Notes

- Audit logs are **append-only** (no updates or deletes)
- **Transactional**: Audit writes participate in same transaction as business operations
- **Field comparison**: Uses reflection-based `EntityComparator` to detect changes
- **JSONB storage**: Field changes stored efficiently with PostgreSQL JSONB + hypersistence-utils
- **Request tracking**: Automatic via `RequestCorrelationFilter` (MDC + ThreadLocal)

---

## Active Technologies
- PostgreSQL (existing), new RefreshToken table via Liquibase migration (001-jwt-api-auth)
- PostgreSQL with Liquibase migrations (001-audit-log)
- Java 21 + Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Spring Security (002-audit-log)
- Hypersistence Utils 3.7.3 for JSONB support (002-audit-log)

## Recent Changes
- 002-audit-log: Added comprehensive admin audit logging for Todo and Invoice entities
- 001-jwt-api-auth: Added Java 21
