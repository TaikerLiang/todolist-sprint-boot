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

**Important**: Hibernate is configured with `ddl-auto=update` which auto-generates schema changes. Liquibase is configured for migration management but migrations must be explicitly generated.

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

### Generate a diff changelog (after modifying JPA entities)
```bash
./mvnw liquibase:diff
```

This compares the Hibernate entity models in `com.example.todolist.model` against the actual PostgreSQL database and generates a changelog at `src/main/resources/db/changelog/generated-changelog.yaml`.

### Apply migrations manually
```bash
./mvnw liquibase:update
```

Note: The Liquibase plugin is configured to use environment variables `DB_URL`, `DB_USER`, and `DB_PASSWORD` (not the Spring Boot variants with different names).

## Development Notes

- The project uses Java 21 features; ensure compiler source/target are set to 21
- All timestamps should use `Instant` (UTC) rather than `LocalDateTime` to avoid timezone issues
- JPA timezone is configured to UTC in `application.properties`
- SQL queries are logged and formatted in console when running (see `spring.jpa.show-sql` and `hibernate.format_sql`)
