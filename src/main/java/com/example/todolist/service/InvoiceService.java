package com.example.todolist.service;

import com.example.todolist.model.Invoice;
import com.example.todolist.repository.InvoiceRepository;
import com.example.todolist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing Invoice entities with integrated audit logging.
 *
 * <p>All CUD operations automatically capture audit logs via {@link AuditCaptureService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final AuditCaptureService auditCaptureService;

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @Transactional
    public Invoice createInvoice(Invoice invoice) {
        // Validate user exists
        if (invoice.getUser() == null || invoice.getUser().getId() == null) {
            throw new RuntimeException("User is required");
        }

        userRepository.findById(invoice.getUser().getId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + invoice.getUser().getId()));

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Capture audit log for CREATE operation
        auditCaptureService.captureCreate("Invoice", savedInvoice.getId());

        return savedInvoice;
    }

    @Transactional
    public Invoice updateInvoice(Long id, Invoice updatedInvoice) {
        return invoiceRepository.findById(id)
            .map(originalInvoice -> {
                // Create a deep copy of original for audit comparison
                Invoice beforeUpdate = cloneInvoice(originalInvoice);

                originalInvoice.setAmount(updatedInvoice.getAmount());
                originalInvoice.setStatus(updatedInvoice.getStatus());
                originalInvoice.setLevel(updatedInvoice.getLevel());

                // Validate and update user if provided
                if (updatedInvoice.getUser() != null && updatedInvoice.getUser().getId() != null) {
                    userRepository.findById(updatedInvoice.getUser().getId())
                        .orElseThrow(() -> new RuntimeException("User not found with id: " + updatedInvoice.getUser().getId()));
                    originalInvoice.setUser(updatedInvoice.getUser());
                }

                Invoice savedInvoice = invoiceRepository.save(originalInvoice);

                // Capture audit log for UPDATE operation with before/after comparison
                auditCaptureService.captureUpdate("Invoice", id, beforeUpdate, savedInvoice);

                return savedInvoice;
            })
            .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
    }

    @Transactional
    public void deleteInvoice(Long id) {
        // Verify invoice exists before deletion
        invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));

        // Delete the invoice
        invoiceRepository.deleteById(id);

        // Capture audit log for DELETE operation AFTER deletion
        auditCaptureService.captureDelete("Invoice", id);
    }

    /**
     * Helper method to clone an Invoice for audit comparison.
     */
    private Invoice cloneInvoice(Invoice invoice) {
        Invoice clone = new Invoice();
        clone.setId(invoice.getId());
        clone.setInvoiceId(invoice.getInvoiceId());
        clone.setAmount(invoice.getAmount());
        clone.setStatus(invoice.getStatus());
        clone.setLevel(invoice.getLevel());
        clone.setUser(invoice.getUser());
        clone.setCreatedAt(invoice.getCreatedAt());
        clone.setUpdatedAt(invoice.getUpdatedAt());
        return clone;
    }
}
