package com.example.todolist.repository;

import com.example.todolist.model.UserActionLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Base repository for {@link UserActionLogs} providing standard CRUD operations.
 *
 * <p>Extends {@link UserActionLogsRepositoryCustom} for custom query methods
 * following the project's repository pattern (Custom/Impl).
 */
@Repository
public interface UserActionLogsRepository
        extends JpaRepository<UserActionLogs, UUID>, UserActionLogsRepositoryCustom {
    // Base CRUD operations provided by JpaRepository
    // Custom queries defined in UserActionLogsRepositoryCustom
}
