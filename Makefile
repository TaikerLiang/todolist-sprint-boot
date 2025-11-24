# Makefile for Liquibase Migration Management
# Provides Django-like commands for database migrations

.PHONY: help makemigration migrate migrate-one migrate-to showmigrations rollback rollback-preview fake-migrate fake-migrate-to fake-migrate-preview

# Default target
help:
	@echo "=== Liquibase Migration Commands ==="
	@echo ""
	@echo "Migration Generation:"
	@echo "  make makemigration              - Generate new migration (auto-numbered)"
	@echo "  make makemigration NAME=example - Generate migration with custom name"
	@echo ""
	@echo "Migration Execution:"
	@echo "  make migrate                    - Apply all pending migrations"
	@echo "  make migrate-one                - Apply only the next pending migration"
	@echo "  make migrate-to NUM=0008        - Migrate to specific version"
	@echo "  make showmigrations             - Show migration status"
	@echo ""
	@echo "Rollback:"
	@echo "  make rollback COUNT=1           - Rollback N changesets (default: 1)"
	@echo "  make rollback-preview COUNT=1   - Preview rollback SQL"
	@echo ""
	@echo "Fake Migrations:"
	@echo "  make fake-migrate               - Mark all pending as executed"
	@echo "  make fake-migrate-to NUM=0008   - Mark up to version as executed"
	@echo "  make fake-migrate-preview       - Preview what would be marked"
	@echo ""

# Variables
CHANGES_DIR := src/main/resources/db/changelog/changes
MVN := mvn
NAME ?= auto_generated
COUNT ?= 1

# Auto-detect next migration number
LATEST_NUM := $(shell ls $(CHANGES_DIR) 2>/dev/null | grep -E '^[0-9]+' | sed 's/^0*//' | sed 's/[^0-9].*//' | sort -n | tail -1)
ifeq ($(LATEST_NUM),)
	NEXT_NUM := 1
else
	NEXT_NUM := $(shell echo $$(($(LATEST_NUM) + 1)))
endif
NEXT_FORMATTED := $(shell printf "%04d" $(NEXT_NUM))

# Migration Generation
makemigration:
	@echo "Generating migration $(NEXT_FORMATTED)_$(NAME).yaml..."
	@if [ ! -d "$(CHANGES_DIR)" ]; then \
		echo "Error: Directory $(CHANGES_DIR) does not exist"; \
		exit 1; \
	fi
	@FILEPATH="$(CHANGES_DIR)/$(NEXT_FORMATTED)_$(NAME).yaml"; \
	$(MVN) liquibase:diff -Dliquibase.diffChangeLogFile=$$FILEPATH; \
	if [ -f $$FILEPATH ]; then \
		echo "" >> $$FILEPATH; \
		echo "- changeSet:" >> $$FILEPATH; \
		echo "    id: tag-$(NEXT_FORMATTED)" >> $$FILEPATH; \
		echo "    author: taiker" >> $$FILEPATH; \
		echo "    changes:" >> $$FILEPATH; \
		echo "      - tagDatabase:" >> $$FILEPATH; \
		echo "          tag: \"$(NEXT_FORMATTED)\"" >> $$FILEPATH; \
		echo "✓ Migration created: $$FILEPATH"; \
		echo "✓ Tag $(NEXT_FORMATTED) added"; \
	else \
		echo "Error: Failed to generate migration file"; \
		exit 1; \
	fi

# Migration Execution
migrate:
	@echo "Applying all pending migrations..."
	@$(MVN) liquibase:update

migrate-one:
	@echo "Applying next pending migration..."
	@$(MVN) liquibase:updateCount -Dliquibase.count=1

migrate-to:
	@if [ -z "$(NUM)" ]; then \
		echo "Error: NUM parameter required. Usage: make migrate-to NUM=0008"; \
		exit 1; \
	fi
	@echo "Migrating to version $(NUM)..."
	@$(MVN) liquibase:updateToTag -Dliquibase.toTag=$(NUM)

showmigrations:
	@echo "Checking migration status..."
	@$(MVN) liquibase:status

# Rollback
rollback:
	@echo "Rolling back $(COUNT) changeset(s)..."
	@$(MVN) liquibase:rollback -Dliquibase.rollbackCount=$(COUNT)

rollback-preview:
	@echo "Previewing rollback of $(COUNT) changeset(s)..."
	@$(MVN) liquibase:rollbackSQL -Dliquibase.rollbackCount=$(COUNT)
	@echo ""
	@echo "Preview saved to: target/liquibase/migrate.sql"

# Fake Migrations
fake-migrate:
	@echo "Marking all pending migrations as executed (without running them)..."
	@$(MVN) liquibase:changeLogSync

fake-migrate-to:
	@if [ -z "$(NUM)" ]; then \
		echo "Error: NUM parameter required. Usage: make fake-migrate-to NUM=0008"; \
		exit 1; \
	fi
	@echo "Marking migrations up to $(NUM) as executed (without running them)..."
	@$(MVN) liquibase:changeLogSyncToTag -Dliquibase.toTag=$(NUM)

fake-migrate-preview:
	@echo "Previewing what would be marked as executed..."
	@$(MVN) liquibase:changeLogSyncSQL
	@echo ""
	@echo "Preview saved to: target/liquibase/migrate.sql"
