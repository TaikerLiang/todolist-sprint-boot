package com.example.todolist.repository;

import com.example.todolist.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Invoice} entity providing standard CRUD operations.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    // Base CRUD operations provided by JpaRepository
}
