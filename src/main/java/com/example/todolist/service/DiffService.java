package com.example.todolist.service;

import com.example.todolist.model.Invoice;
import com.example.todolist.model.Todo;
import com.example.todolist.repository.InvoiceRepository;
import com.example.todolist.repository.TodoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for generating field-by-field diffs for approval requests.
 * Shows old vs new values for UPDATE operations.
 */
@Service
public class DiffService {

    private final TodoRepository todoRepository;
    private final InvoiceRepository invoiceRepository;

    @Autowired
    public DiffService(TodoRepository todoRepository, InvoiceRepository invoiceRepository) {
        this.todoRepository = todoRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Generate a diff for a Todo item
     *
     * @param operation The operation type (CREATE, UPDATE, DELETE)
     * @param itemId The item ID (null for CREATE)
     * @param requestedData The requested changes
     * @return List of field diffs
     */
    public List<FieldDiff> generateTodoDiff(String operation, Long itemId, Map<String, Object> requestedData) {
        List<FieldDiff> diffs = new ArrayList<>();

        switch (operation) {
            case "CREATE":
                // For CREATE, show only new values
                addIfPresent(diffs, "title", null, requestedData.get("title"));
                addIfPresent(diffs, "description", null, requestedData.get("description"));
                addIfPresent(diffs, "level", null, requestedData.get("level"));
                addIfPresent(diffs, "completed", null, requestedData.get("completed"));
                break;

            case "UPDATE":
                // For UPDATE, show old vs new
                if (itemId == null) {
                    throw new IllegalArgumentException("Item ID required for UPDATE operation");
                }

                Optional<Todo> todoOpt = todoRepository.findById(itemId);
                if (todoOpt.isEmpty()) {
                    throw new IllegalArgumentException("Todo not found: " + itemId);
                }

                Todo currentTodo = todoOpt.get();
                compareField(diffs, "title", currentTodo.getTitle(), requestedData.get("title"));
                compareField(diffs, "description", currentTodo.getDescription(), requestedData.get("description"));
                compareField(diffs, "level", currentTodo.getLevel(), requestedData.get("level"));
                compareField(diffs, "completed", currentTodo.isCompleted(), requestedData.get("completed"));
                break;

            case "DELETE":
                // For DELETE, show current values that will be removed
                if (itemId == null) {
                    throw new IllegalArgumentException("Item ID required for DELETE operation");
                }

                Optional<Todo> todoToDelete = todoRepository.findById(itemId);
                if (todoToDelete.isEmpty()) {
                    throw new IllegalArgumentException("Todo not found: " + itemId);
                }

                Todo current = todoToDelete.get();
                addIfPresent(diffs, "title", current.getTitle(), null);
                addIfPresent(diffs, "description", current.getDescription(), null);
                addIfPresent(diffs, "level", current.getLevel(), null);
                addIfPresent(diffs, "completed", current.isCompleted(), null);
                break;
        }

        return diffs;
    }

    /**
     * Generate a diff for an Invoice item
     *
     * @param operation The operation type (CREATE, UPDATE, DELETE)
     * @param itemId The item ID (null for CREATE)
     * @param requestedData The requested changes
     * @return List of field diffs
     */
    public List<FieldDiff> generateInvoiceDiff(String operation, Long itemId, Map<String, Object> requestedData) {
        List<FieldDiff> diffs = new ArrayList<>();

        switch (operation) {
            case "CREATE":
                // For CREATE, show only new values
                addIfPresent(diffs, "invoiceId", null, requestedData.get("invoiceId"));
                addIfPresent(diffs, "amount", null, requestedData.get("amount"));
                addIfPresent(diffs, "status", null, requestedData.get("status"));
                addIfPresent(diffs, "level", null, requestedData.get("level"));
                break;

            case "UPDATE":
                // For UPDATE, show old vs new
                if (itemId == null) {
                    throw new IllegalArgumentException("Item ID required for UPDATE operation");
                }

                Optional<Invoice> invoiceOpt = invoiceRepository.findById(itemId);
                if (invoiceOpt.isEmpty()) {
                    throw new IllegalArgumentException("Invoice not found: " + itemId);
                }

                Invoice currentInvoice = invoiceOpt.get();
                compareField(diffs, "amount", currentInvoice.getAmount(), requestedData.get("amount"));
                compareField(diffs, "status", currentInvoice.getStatus(), requestedData.get("status"));
                compareField(diffs, "level", currentInvoice.getLevel(), requestedData.get("level"));
                break;

            case "DELETE":
                // For DELETE, show current values that will be removed
                if (itemId == null) {
                    throw new IllegalArgumentException("Item ID required for DELETE operation");
                }

                Optional<Invoice> invoiceToDelete = invoiceRepository.findById(itemId);
                if (invoiceToDelete.isEmpty()) {
                    throw new IllegalArgumentException("Invoice not found: " + itemId);
                }

                Invoice current = invoiceToDelete.get();
                addIfPresent(diffs, "invoiceId", current.getInvoiceId(), null);
                addIfPresent(diffs, "amount", current.getAmount(), null);
                addIfPresent(diffs, "status", current.getStatus(), null);
                addIfPresent(diffs, "level", current.getLevel(), null);
                break;
        }

        return diffs;
    }

    /**
     * Generate a diff based on item type
     */
    public List<FieldDiff> generateDiff(String itemType, String operation, Long itemId, Map<String, Object> requestedData) {
        switch (itemType) {
            case "TODO":
                return generateTodoDiff(operation, itemId, requestedData);
            case "INVOICE":
                return generateInvoiceDiff(operation, itemId, requestedData);
            default:
                throw new IllegalArgumentException("Unsupported item type: " + itemType);
        }
    }

    // Helper methods

    private void compareField(List<FieldDiff> diffs, String fieldName, Object oldValue, Object newValue) {
        if (newValue != null && !Objects.equals(oldValue, newValue)) {
            diffs.add(new FieldDiff(fieldName, oldValue, newValue));
        }
    }

    private void addIfPresent(List<FieldDiff> diffs, String fieldName, Object oldValue, Object newValue) {
        if (newValue != null || oldValue != null) {
            diffs.add(new FieldDiff(fieldName, oldValue, newValue));
        }
    }

    /**
     * DTO representing a field difference
     */
    public static class FieldDiff {
        private final String fieldName;
        private final Object oldValue;
        private final Object newValue;

        public FieldDiff(String fieldName, Object oldValue, Object newValue) {
            this.fieldName = fieldName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public Object getOldValue() {
            return oldValue;
        }

        public Object getNewValue() {
            return newValue;
        }

        public String getChangeType() {
            if (oldValue == null) return "ADDED";
            if (newValue == null) return "REMOVED";
            return "MODIFIED";
        }
    }
}
