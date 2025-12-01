# Todo List API

A RESTful API for managing todo items built with Spring Boot 3.5.7, PostgreSQL, and Liquibase for database migrations.

## Features

- Create, read, update, and delete todo items
- RESTful API with JSON responses
- PostgreSQL database with Liquibase migrations
- Lombok for boilerplate reduction
- UTC-safe timestamp handling
- Snake_case database naming convention

## Tech Stack

- **Java**: 21
- **Spring Boot**: 3.5.7
- **Database**: PostgreSQL
- **ORM**: Spring Data JPA with Hibernate
- **Migrations**: Liquibase 4.27.0
- **Build Tool**: Maven 3.9.11
- **Utilities**: Lombok 1.18.36

## Prerequisites

- Java 21 (OpenJDK recommended)
- Maven 3.9.11+
- PostgreSQL database
- Docker (optional, for running PostgreSQL)

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd todolist
```

### 2. Configure environment variables

Create a `.env` file in the project root:

```bash
# Database configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=demo
DB_USER=default_user
DB_PASSWORD=default_pass

# For Liquibase commands
DB_URL=jdbc:postgresql://localhost:5432/demo

# Java version (important for Maven)
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
```

### 3. Start PostgreSQL

**Option A: Using Docker**
```bash
docker run -d \
  --name todolist-postgres \
  -e POSTGRES_DB=demo \
  -e POSTGRES_USER=default_user \
  -e POSTGRES_PASSWORD=default_pass \
  -p 5432:5432 \
  postgres:16
```

**Option B: Using local PostgreSQL**
```bash
# Create database
psql -U postgres
CREATE DATABASE demo;
CREATE USER default_user WITH PASSWORD 'default_pass';
GRANT ALL PRIVILEGES ON DATABASE demo TO default_user;
```

### 4. Run database migrations

```bash
make migrate
```

## Running the Application

### Using Makefile (recommended)

```bash
# Start on default port (8080)
make run

# Start on custom port
make run PORT=9090
```

### Using Maven directly

```bash
# Export environment variables first
export DB_HOST=localhost
export JAVA_HOME=/path/to/java21

# Run application
mvn spring-boot:run

# Or on custom port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090"
```

### Building JAR

```bash
# Build executable JAR
mvn clean package

# Run the JAR
java -jar target/todolist-0.0.1-SNAPSHOT.jar
```

## API Endpoints

Base URL: `http://localhost:8080/api/todos`

### Get all todos
```http
GET /api/todos
```

**Response:**
```json
[
  {
    "id": 1,
    "title": "Buy groceries",
    "description": "Milk, eggs, bread",
    "completed": false,
    "createdAt": "2025-11-30T10:00:00Z"
  }
]
```

### Create a todo
```http
POST /api/todos
Content-Type: application/json

{
  "title": "Buy groceries",
  "description": "Milk, eggs, bread",
  "completed": false
}
```

### Update a todo
```http
PUT /api/todos/{id}
Content-Type: application/json

{
  "title": "Buy groceries",
  "description": "Milk, eggs, bread, cheese",
  "completed": true
}
```

### Delete a todo
```http
DELETE /api/todos/{id}
```

## Database Migrations

This project uses Liquibase with a Django-style Makefile wrapper for managing database migrations.

### Generate a new migration

```bash
# Auto-numbered migration
make makemigration

# With custom name
make makemigration NAME=add_priority_field
```

### Apply migrations

```bash
# Apply all pending migrations
make migrate

# Apply only the next pending migration
make migrate-one

# Migrate to specific version
make migrate-to NUM=0008
```

### Check migration status

```bash
make showmigrations
```

### Rollback migrations

```bash
# Rollback 1 changeset
make rollback

# Rollback N changesets
make rollback COUNT=3

# Preview rollback SQL
make rollback-preview COUNT=1
```

### Fake migrations

```bash
# Mark all pending as executed
make fake-migrate

# Mark up to version as executed
make fake-migrate-to NUM=0008
```

## Project Structure

```
todolist/
├── src/
│   ├── main/
│   │   ├── java/com/example/todolist/
│   │   │   ├── controller/       # REST controllers
│   │   │   ├── model/            # JPA entities
│   │   │   ├── repository/       # Spring Data repositories
│   │   │   ├── service/          # Business logic
│   │   │   └── TodolistApplication.java
│   │   └── resources/
│   │       ├── db/changelog/     # Liquibase migrations
│   │       │   ├── changes/      # Migration files
│   │       │   └── db.changelog-master.yaml
│   │       └── application.properties
│   └── test/
├── .env                          # Environment variables
├── Makefile                      # Build and migration commands
├── pom.xml                       # Maven configuration
└── README.md
```

## Development Notes

### Timestamps

All timestamps use `Instant` (UTC) rather than `LocalDateTime` to avoid timezone issues. The JPA timezone is configured to UTC in `application.properties`.

### Database Naming

The application uses snake_case for database column names via Hibernate's `CamelCaseToUnderscoresNamingStrategy`.

### Lombok

Entity classes use Lombok annotations (`@Getter`, `@Setter`, `@NoArgsConstructor`) to reduce boilerplate code.

### DDL Auto-generation

Hibernate is configured with `ddl-auto=none` to prevent automatic schema changes. All schema modifications must be done through Liquibase migrations.

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TodolistApplicationTests

# Run specific test method
mvn test -Dtest=ClassName#methodName
```

## Makefile Commands

Run `make help` to see all available commands:

```bash
make help
```

### Application Commands
- `make run` - Start Spring Boot application (default port 8080)
- `make run PORT=9090` - Start on custom port

### Migration Commands
- `make makemigration` - Generate new migration
- `make migrate` - Apply all pending migrations
- `make showmigrations` - Show migration status
- `make rollback` - Rollback migrations

## Troubleshooting

### Build fails with "cannot find symbol: method getTitle()"

Ensure Maven is using Java 21 and Lombok is properly configured:

```bash
# Check Maven Java version
mvn -version

# Set JAVA_HOME in .env file
echo 'JAVA_HOME=/path/to/java21' >> .env
```

### Connection refused to PostgreSQL

Verify PostgreSQL is running and environment variables are set:

```bash
# Check if PostgreSQL is running
docker ps  # or
pg_isready

# Verify .env file contains correct database configuration
cat .env
```

### Liquibase migration fails

Ensure `DB_URL` environment variable is set for Liquibase commands:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/demo
make migrate
```

## License

This project is a demo application for learning Spring Boot.

## Contributing

This is a demo project. Feel free to fork and experiment!
