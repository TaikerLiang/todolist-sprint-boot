# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.7 todo list application using:
- Java 21
- PostgreSQL database
- Spring Data JPA with Hibernate
- Liquibase for database migrations
- Maven for build management

## Architecture

The application follows a standard Spring Boot layered architecture:

**Model Layer** (`com.example.todolist.model`): JPA entities representing database tables
- Uses `Instant` for timestamps (UTC-safe) rather than `LocalDateTime`
- Entities configured with `@Table` and `@Column` annotations with appropriate constraints

**Repository Layer** (`com.example.todolist.repository`): Spring Data JPA repositories
- Extends `JpaRepository` for standard CRUD operations

**Service Layer** (`com.example.todolist.service`): Business logic
- Handles business rules and orchestrates repository calls

**Controller Layer** (`com.example.todolist.controller`): REST API endpoints
- Uses `@RestController` for RESTful web services

## Database Configuration

The application uses PostgreSQL and requires environment variables:
- `DB_HOST`: Database host
- `DB_PORT`: Database port (defaults to 5432)
- `DB_NAME`: Database name (defaults to "demo")
- `DB_USER`: Database username (defaults to "default_user")
- `DB_PASSWORD`: Database password (defaults to "default_pass")

For Liquibase commands, set `DB_URL` environment variable instead of the individual components.

**Important**: Hibernate is configured with `ddl-auto=none` to prevent automatic schema changes. All schema modifications must be done through Liquibase migrations which must be explicitly generated.

## Build and Run Commands

### Build the application
```bash
./mvnw clean package
```

### Run the application
```bash
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```

### Run a single test class
```bash
./mvnw test -Dtest=TodolistApplicationTests
```

### Run a specific test method
```bash
./mvnw test -Dtest=ClassName#methodName
```

## Database Migration with Liquibase

This project uses Liquibase with a Django-style Makefile wrapper for managing database migrations.

### Prerequisites
Ensure you have a `.env` file in the project root with the database configuration:
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=demo
DB_USER=default_user
DB_PASSWORD=default_pass
DB_URL=jdbc:postgresql://localhost:5432/demo
```

### Generate a new migration (after modifying JPA entities)

**Recommended: Using Makefile**
```bash
# Auto-numbered migration
make makemigration

# With custom descriptive name
make makemigration NAME=add_level_to_todo
```

This compares the Hibernate entity models in `com.example.todolist.model` against the actual PostgreSQL database and generates a numbered changelog file in `src/main/resources/db/changelog/changes/`.

**Alternative: Using Maven directly**
```bash
./mvnw liquibase:diff
```

### Apply migrations

**Recommended: Using Makefile**
```bash
# Apply all pending migrations
make migrate

# Apply only the next pending migration
make migrate-one

# Migrate to specific version
make migrate-to NUM=0008
```

**Alternative: Using Maven directly**
```bash
./mvnw liquibase:update
```

### Check migration status
```bash
make showmigrations
```

### Rollback migrations
```bash
# Rollback 1 changeset (default)
make rollback

# Rollback N changesets
make rollback COUNT=3

# Preview rollback SQL without executing
make rollback-preview COUNT=1
```

### Fake migrations (mark as executed without running)
```bash
# Mark all pending migrations as executed
make fake-migrate

# Mark migrations up to a specific version as executed
make fake-migrate-to NUM=0008
```

Note: The Liquibase plugin is configured to use environment variables `DB_URL`, `DB_USER`, and `DB_PASSWORD` (not the Spring Boot variants with different names). The Makefile automatically loads these from the `.env` file.

## Development Notes

- The project uses Java 21 features; ensure compiler source/target are set to 21
- All timestamps should use `Instant` (UTC) rather than `LocalDateTime` to avoid timezone issues
- JPA timezone is configured to UTC in `application.properties`
- SQL queries are logged and formatted in console when running (see `spring.jpa.show-sql` and `hibernate.format_sql`)

## Active Technologies
- Java 21 + Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Liquibase 4.27.0, PostgreSQL driver (001-approval-workflow)
- PostgreSQL database (existing) (001-approval-workflow)

## Recent Changes
- 001-approval-workflow: Added Java 21 + Spring Boot 3.1.5, Spring Data JPA, Hibernate 6, Liquibase 4.27.0, PostgreSQL driver
