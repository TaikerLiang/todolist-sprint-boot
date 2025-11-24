# Migration Commands - Makefile Documentation

## Overview

This Makefile provides a Django-like interface for managing Liquibase database migrations in the Spring Boot todo list application. It wraps Maven Liquibase commands with simpler, more intuitive commands and adds automatic features like sequence number detection and tagging.

## Benefits

- **Auto-sequencing**: Automatically detects the next migration number
- **Auto-tagging**: Adds tags to each migration for version-specific operations
- **Simplified commands**: Easy-to-remember commands similar to Django
- **Granular control**: Migrate to specific versions, rollback by count, fake migrations

## Command Reference

### Migration Generation

#### `make makemigration`
Auto-generates a new migration file with the next sequence number.

**Behavior:**
- Scans `src/main/resources/db/changelog/changes/` for existing migrations
- Finds the highest sequence number (e.g., 0002)
- Increments by 1 to get next number (0003)
- Generates file: `000X_auto_generated.yaml`
- Automatically appends a tag changeset (e.g., tag "0003")

**Example:**
```bash
make makemigration
# Generates: src/main/resources/db/changelog/changes/0003_auto_generated.yaml
```

**Generated file structure:**
```yaml
databaseChangeLog:
  - changeSet:
      id: 0003-auto_generated
      author: taiker
      changes:
        # Auto-generated changes from liquibase:diff
  - changeSet:
      id: tag-0003
      author: taiker
      changes:
        - tagDatabase:
            tag: "0003"
```

#### `make makemigration NAME=example`
Generates a migration with a custom descriptive name.

**Example:**
```bash
make makemigration NAME=add_user_table
# Generates: 0003_add_user_table.yaml
```

**Use case:** Provide meaningful names for your migrations instead of generic "auto_generated"

---

### Migration Execution

#### `make migrate`
Applies all pending migrations to the database.

**Maven command:**
```bash
mvn liquibase:update
```

**Example:**
```bash
make migrate
# Applies all pending changesets
```

#### `make migrate-one`
Applies only the next pending migration.

**Maven command:**
```bash
mvn liquibase:updateCount -Dliquibase.count=1
```

**Example:**
```bash
make migrate-one
# Applies only the next changeset
```

**Use case:** Test migrations incrementally

#### `make migrate-to NUM=0008`
Migrates to a specific version using tags (similar to Django's `migrate app_name 0008`).

**Maven command:**
```bash
mvn liquibase:updateToTag -Dliquibase.toTag=0008
```

**Example:**
```bash
make migrate-to NUM=0008
# Applies migrations up to and including 0008
```

**Requirements:**
- Each migration must have a tag changeset (automatically added by `make makemigration`)
- Tag name matches the sequence number

**Use case:**
- Deploy to a specific version in staging/production
- Rollback to a known good state by migrating to an earlier version

#### `make showmigrations`
Displays the status of all migrations (pending, executed, etc.).

**Maven command:**
```bash
mvn liquibase:status
```

**Example:**
```bash
make showmigrations
# Shows which migrations have been applied and which are pending
```

---

### Rollback Commands

#### `make rollback COUNT=1`
Rollback the last N changesets (default: 1).

**Maven command:**
```bash
mvn liquibase:rollback -Dliquibase.rollbackCount=1
```

**Examples:**
```bash
make rollback              # Rollback last changeset
make rollback COUNT=3      # Rollback last 3 changesets
```

**Use case:** Undo recent migrations that caused issues

#### `make rollback-preview COUNT=1`
Preview the rollback SQL without executing (dry run).

**Maven command:**
```bash
mvn liquibase:rollbackSQL -Dliquibase.rollbackCount=1
```

**Example:**
```bash
make rollback-preview COUNT=2
# Shows SQL that would be executed to rollback 2 changesets
# Output saved to: target/liquibase/migrate.sql
```

**Use case:** Verify rollback safety before executing

---

### Fake Migration Commands

Fake migrations mark changesets as executed in the database changelog without actually running the SQL. This is useful for syncing migration state with manually applied schema changes.

#### `make fake-migrate`
Mark ALL pending changesets as executed without running them.

**Maven command:**
```bash
mvn liquibase:changeLogSync
```

**Example:**
```bash
make fake-migrate
# Marks all pending migrations as executed
```

**Use case:**
- Schema changes were applied manually
- Initializing Liquibase on an existing database

#### `make fake-migrate-to NUM=0008`
Mark changesets up to a specific version as executed (using tags).

**Maven command:**
```bash
mvn liquibase:changeLogSyncToTag -Dliquibase.toTag=0008
```

**Example:**
```bash
make fake-migrate-to NUM=0008
# Marks migrations 0001-0008 as executed without running them
```

**Use case:**
- Some migrations were applied manually, others need to run
- Selective sync of migration state

#### `make fake-migrate-preview`
Preview what would be marked as executed (dry run).

**Maven command:**
```bash
mvn liquibase:changeLogSyncSQL
```

**Example:**
```bash
make fake-migrate-preview
# Shows which migrations would be marked as executed
```

---

### Utility Commands

#### `make help`
Displays all available commands with brief descriptions (default target).

**Example:**
```bash
make           # Shows help
make help      # Shows help
```

---

## Implementation Details

### Auto-Detection Algorithm

1. **Scan directory:** `src/main/resources/db/changelog/changes/`
2. **Find migration files:** Match pattern `*.yaml`
3. **Extract sequence numbers:** Parse leading digits (0001, 0002, etc.)
4. **Find maximum:** Sort numerically and get highest number
5. **Increment:** Add 1 to get next sequence number
6. **Format:** Use 4-digit padding with leading zeros

**Shell logic:**
```bash
# Find latest migration number
LATEST=$(ls src/main/resources/db/changelog/changes/ | \
         grep -E '^[0-9]+' | \
         sed 's/^0*//' | \
         sed 's/[^0-9].*//' | \
         sort -n | \
         tail -1)

# Increment and format
NEXT=$((LATEST + 1))
NEXT_FORMATTED=$(printf "%04d" $NEXT)
```

### Auto-Tagging

When generating a migration, automatically append a tag changeset:

```yaml
- changeSet:
    id: tag-XXXX
    author: taiker
    changes:
      - tagDatabase:
          tag: "XXXX"
```

Where `XXXX` is the sequence number (e.g., "0003").

### File Naming Convention

- **Format:** `NNNN_description.yaml`
- **NNNN:** 4-digit sequence number with leading zeros (0001, 0002, ..., 0010, etc.)
- **description:** Descriptive name (lowercase, underscores for spaces)

**Examples:**
- `0001_init.yaml`
- `0002_rename_createdAt_to_created_at.yaml`
- `0003_add_user_table.yaml`
- `0010_auto_generated.yaml`

### Tag Naming Convention

- **Format:** Tag value is the sequence number without leading zeros
- **Example:** For file `0003_example.yaml`, tag is `"0003"`

This allows commands like `make migrate-to NUM=0003` to work correctly.

---

## Technical Specifications

### Paths

- **Migrations directory:** `src/main/resources/db/changelog/changes/`
- **Master changelog:** `src/main/resources/db/changelog/db.changelog-master.yaml`
- **Generated SQL output:** `target/liquibase/migrate.sql`

### Maven Configuration

- **Maven executable:** `mvn` (system Maven, not `./mvnw`)
- **Liquibase version:** 4.27.0 (from pom.xml)
- **Database:** PostgreSQL

### Environment Variables

Required for Liquibase commands:
- `DB_URL`: Database JDBC URL
- `DB_USER`: Database username
- `DB_PASSWORD`: Database password

---

## Common Workflows

### Workflow 1: Creating and Applying a New Migration

```bash
# 1. Make changes to JPA entities in src/main/java/com/example/todolist/model/

# 2. Generate migration (auto-detects as 0003)
make makemigration NAME=add_priority_field

# 3. Review generated file
cat src/main/resources/db/changelog/changes/0003_add_priority_field.yaml

# 4. Apply migration
make migrate

# 5. Verify status
make showmigrations
```

### Workflow 2: Rolling Back a Migration

```bash
# 1. Preview what would be rolled back
make rollback-preview

# 2. Review the SQL in target/liquibase/migrate.sql

# 3. Execute rollback
make rollback

# 4. Verify status
make showmigrations
```

### Workflow 3: Deploying to a Specific Version

```bash
# Deploy to version 0008 in staging
make migrate-to NUM=0008

# Verify correct version
make showmigrations
```

### Workflow 4: Syncing with Manual Changes

```bash
# Schema was manually updated, need to sync migration 0005
make fake-migrate-to NUM=0005

# Now apply remaining migrations normally
make migrate
```

### Workflow 5: Creating Multiple Migrations

```bash
# Create first migration
make makemigration NAME=add_user_table
# Generates: 0003_add_user_table.yaml

# Create second migration
make makemigration NAME=add_indexes
# Generates: 0004_add_indexes.yaml (auto-incremented)

# Apply both
make migrate
```

---

## Comparison with Django

| Django Command | Makefile Command | Description |
|----------------|------------------|-------------|
| `python manage.py makemigrations` | `make makemigration` | Generate migration |
| `python manage.py migrate` | `make migrate` | Apply all migrations |
| `python manage.py migrate app_name 0008` | `make migrate-to NUM=0008` | Migrate to version |
| `python manage.py showmigrations` | `make showmigrations` | Show migration status |
| `python manage.py migrate app_name zero` | `make rollback COUNT=N` | Rollback migrations |
| `python manage.py migrate --fake` | `make fake-migrate` | Mark as executed |
| `python manage.py migrate app_name 0008 --fake` | `make fake-migrate-to NUM=0008` | Mark version as executed |

---

## Notes

- The master changelog uses `includeAll` to automatically discover migration files
- Migrations are applied in alphabetical order (hence the numeric prefix)
- Liquibase tracks applied changesets by `id + author + filename`
- Tags are stored in the `databasechangelog` table
- Most operations require database environment variables to be set

---

## Future Enhancements

Potential additions to consider:

1. **`make migrate-back NUM=0005`** - Rollback to a specific version (instead of by count)
2. **`make squash`** - Combine multiple migrations into one (for optimization)
3. **`make validate`** - Validate migrations without applying
4. **`make clear-checksums`** - Clear checksums for modified changesets
5. **Auto-backup** before migrations/rollbacks
6. **Colored output** for better readability
7. **Interactive mode** for confirmation on destructive operations
