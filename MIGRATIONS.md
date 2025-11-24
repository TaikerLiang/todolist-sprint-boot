# Database Migrations Made Simple

## The Spirit

Managing database migrations shouldn't require memorizing long Maven commands with cryptic parameters. Inspired by Django's elegant migration system, this Makefile brings the same simplicity and developer-friendly experience to your Spring Boot + Liquibase workflow.

**Philosophy:**
- **Intuitive**: Commands should read like natural language
- **Automatic**: The system should handle the tedious stuff (numbering, tagging)
- **Safe**: Preview changes before applying them
- **Flexible**: Support both simple and advanced workflows

Instead of typing:
```bash
mvn liquibase:diff -Dliquibase.diffChangeLogFile=src/main/resources/db/changelog/changes/0003_add_user_table.yaml
```

Just type:
```bash
make makemigration NAME=add_user_table
```

## Quick Start

### 1. Your First Migration

After modifying your JPA entities (like `Todo.java`), generate a migration:

```bash
make makemigration NAME=add_priority_field
```

**What happens:**
- ✨ Automatically detects this should be migration `0003`
- 📄 Creates `0003_add_priority_field.yaml`
- 🏷️ Adds a tag `"0003"` for version control
- 🔍 Compares your entities with the database to find changes

### 2. Review the Migration

Check what was generated:

```bash
cat src/main/resources/db/changelog/changes/0003_add_priority_field.yaml
```

### 3. Apply the Migration

```bash
make migrate
```

That's it! Your database is now updated.

## Common Workflows

### Creating and Applying Migrations

```bash
# 1. Modify your entity (e.g., add a field to Todo.java)
# 2. Generate migration
make makemigration NAME=add_priority_field

# 3. Apply it
make migrate

# 4. Check status
make showmigrations
```

### Testing Migrations Incrementally

```bash
# Apply one migration at a time
make migrate-one

# Check if it worked
make showmigrations

# Apply the next one
make migrate-one
```

### Undoing Mistakes

```bash
# See what would be rolled back
make rollback-preview

# Review the SQL in target/liquibase/migrate.sql

# Actually rollback
make rollback
```

### Deploying to Specific Versions

Perfect for staging environments or controlled rollouts:

```bash
# Deploy only up to version 0005
make migrate-to NUM=0005

# Later, deploy up to 0008
make migrate-to NUM=0008
```

### Working with Existing Databases

Sometimes you've already applied schema changes manually and just need to sync the migration state:

```bash
# Mark migrations as executed without running them
make fake-migrate-to NUM=0005

# Now apply the rest normally
make migrate
```

## Command Reference

### Essential Commands

| Command | What It Does |
|---------|--------------|
| `make help` | Show all available commands |
| `make makemigration` | Create new migration (auto-numbered) |
| `make migrate` | Apply all pending migrations |
| `make showmigrations` | See what's applied and what's pending |

### Advanced Commands

| Command | What It Does |
|---------|--------------|
| `make migrate-one` | Apply just the next migration |
| `make migrate-to NUM=0008` | Apply migrations up to version 0008 |
| `make rollback` | Undo the last migration |
| `make rollback COUNT=3` | Undo the last 3 migrations |
| `make rollback-preview` | See what rollback would do (safe) |
| `make fake-migrate` | Mark all pending as applied (no SQL) |
| `make fake-migrate-to NUM=0005` | Mark up to version 0005 as applied |

## Real-World Examples

### Example 1: Adding a New Feature

You're adding a "priority" field to todos:

```bash
# 1. Edit src/main/java/com/example/todolist/model/Todo.java
# Add: private Integer priority;

# 2. Generate migration
make makemigration NAME=add_priority_to_todos

# Output:
# ✓ Migration created: src/main/resources/db/changelog/changes/0003_add_priority_to_todos.yaml
# ✓ Tag 0003 added

# 3. Review the generated file (optional)
cat src/main/resources/db/changelog/changes/0003_add_priority_to_todos.yaml

# 4. Apply to development database
make migrate

# 5. Commit both the entity change and the migration
git add .
git commit -m "Add priority field to todos"
```

### Example 2: Fixing a Mistake

You applied a migration but realized there's an issue:

```bash
# 1. See what the rollback would do
make rollback-preview

# 2. Read the SQL that will be executed
cat target/liquibase/migrate.sql

# 3. Rollback the migration
make rollback

# 4. Fix the entity
# Edit Todo.java with the correct change

# 5. Generate a new migration
make makemigration NAME=add_priority_to_todos_fixed

# 6. Apply it
make migrate
```

### Example 3: Production Deployment

You want to deploy carefully to production:

```bash
# On production server

# 1. Check current state
make showmigrations

# 2. Deploy to version 0010 (tested in staging)
make migrate-to NUM=0010

# 3. Verify
make showmigrations

# If something goes wrong:
# 4. Rollback to previous version
make migrate-to NUM=0009
```

### Example 4: Joining a Project

You've just cloned the repo and need to set up your local database:

```bash
# 1. Create the database (PostgreSQL)
createdb todolist

# 2. Set environment variables
export DB_URL=jdbc:postgresql://localhost:5432/todolist
export DB_USER=your_username
export DB_PASSWORD=your_password

# 3. Apply all migrations
make migrate

# 4. Verify
make showmigrations

# You're ready to develop!
```

### Example 5: Syncing with Manual Changes

The DBA applied schema changes directly to production, now you need to sync:

```bash
# 1. The migrations 0001-0005 were applied manually
# Mark them as executed without running SQL
make fake-migrate-to NUM=0005

# 2. Now apply migrations 0006 onwards normally
make migrate

# 3. Verify everything is in sync
make showmigrations
```

## Understanding Auto-Numbering

The system automatically figures out the next migration number:

```bash
# Current state: 0001-init.yaml, 0002-rename-field.yaml

make makemigration NAME=add_user
# Creates: 0003_add_user.yaml

make makemigration NAME=add_indexes
# Creates: 0004_add_indexes.yaml

# Even if you delete 0003, the next one will be 0005
# (It always uses the highest number + 1)
```

## Understanding Tags

Every migration gets a tag automatically. This enables version-specific operations:

```yaml
# In 0003_add_priority.yaml:

databaseChangeLog:
  - changeSet:
      id: 0003-add_priority
      changes:
        - addColumn: ...

  # Auto-added by make makemigration:
  - changeSet:
      id: tag-0003
      changes:
        - tagDatabase:
            tag: "0003"
```

The tag `"0003"` lets you do:
```bash
make migrate-to NUM=0003      # Migrate to this version
make fake-migrate-to NUM=0003  # Mark up to this version as done
```

## Tips & Best Practices

### ✅ DO

- **Use descriptive names**: `make makemigration NAME=add_user_authentication` not `NAME=changes`
- **Review migrations before applying**: Always check the generated file
- **Test rollbacks**: Use `make rollback-preview` before `make rollback`
- **Commit migrations with code**: Your entity changes and migrations should be in the same commit
- **Use `migrate-to` in production**: Gives you precise control over versions

### ❌ DON'T

- **Don't edit applied migrations**: Create a new migration instead
- **Don't skip migrations**: Apply them in order
- **Don't apply untested migrations to production**: Test in dev/staging first
- **Don't use `fake-migrate` unless necessary**: Only for syncing with manual changes

### 💡 Pro Tips

1. **Check status frequently**: `make showmigrations` is your friend
2. **Preview before doing**: Most commands have a `-preview` version
3. **One logical change per migration**: Easier to review and rollback
4. **Name migrations clearly**: Future you will thank present you
5. **Keep migrations small**: Easier to debug when something goes wrong

## Troubleshooting

### "Error: Directory does not exist"

Make sure you're in the project root directory:
```bash
cd /path/to/todolist
make makemigration
```

### "Error: NUM parameter required"

Some commands need a version number:
```bash
make migrate-to NUM=0008  # ✓ Correct
make migrate-to           # ✗ Missing NUM
```

### "No changes detected"

If `make makemigration` generates an empty migration:
- Make sure your entity changes are saved
- Verify Hibernate is scanning your model package
- Check that your changes aren't just comments or formatting

### "Connection refused"

Set your database environment variables:
```bash
export DB_URL=jdbc:postgresql://localhost:5432/todolist
export DB_USER=myuser
export DB_PASSWORD=mypassword
```

## Comparison: Before vs After

### Before (Raw Maven Commands)

```bash
# Generate migration
mvn liquibase:diff -Dliquibase.diffChangeLogFile=src/main/resources/db/changelog/changes/0003_add_user_table.yaml

# Manually edit file to add tag...

# Apply migrations
mvn liquibase:update

# Apply to specific version
mvn liquibase:updateToTag -Dliquibase.toTag=0008

# Rollback
mvn liquibase:rollback -Dliquibase.rollbackCount=1

# Check status
mvn liquibase:status
```

### After (Make Commands)

```bash
# Generate migration (auto-numbered, auto-tagged)
make makemigration NAME=add_user_table

# Apply migrations
make migrate

# Apply to specific version
make migrate-to NUM=0008

# Rollback
make rollback

# Check status
make showmigrations
```

**Result:** Simpler, clearer, faster. ✨

## Learn More

- **Technical Details**: See `plans/migration_cmd.md` for implementation details
- **Liquibase Docs**: [docs.liquibase.com](https://docs.liquibase.com)
- **Project Setup**: See `CLAUDE.md` for project architecture

## Questions?

Run `make help` anytime to see all available commands!

---

**Remember**: Migrations are your database's version control. Treat them with the same care you treat your code. 🚀
