package com.example.todolist.service;

import com.example.todolist.model.Invoice;
import com.example.todolist.repository.InvoiceRepository;
import com.example.todolist.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + id));
    }

    public Invoice createInvoice(Invoice invoice) {
        // Validate user exists
        if (invoice.getUser() == null || invoice.getUser().getId() == null) {
            throw new RuntimeException("User is required");
        }

        userRepository.findById(invoice.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + invoice.getUser().getId()));

        return invoiceRepository.save(invoice);
    }

    public Invoice updateInvoice(Long id, Invoice updatedInvoice) {
        return invoiceRepository.findById(id)
                .map(invoice -> {
                    if (updatedInvoice.getAmount() != null) {
                        invoice.setAmount(updatedInvoice.getAmount());
                    }
                    if (updatedInvoice.getStatus() != null) {
                        invoice.setStatus(updatedInvoice.getStatus());
                    }
                    if (updatedInvoice.getLevel() != null) {
                        invoice.setLevel(updatedInvoice.getLevel());
                    }

                    // Validate and update user if provided
                    if (updatedInvoice.getUser() != null && updatedInvoice.getUser().getId() != null) {
                        userRepository.findById(updatedInvoice.getUser().getId())
                                .orElseThrow(() -> new RuntimeException("User not found with id: " + updatedInvoice.getUser().getId()));
                        invoice.setUser(updatedInvoice.getUser());
                    }

                    return invoiceRepository.save(invoice);
                })
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    public void deleteInvoice(Long id) {
        invoiceRepository.deleteById(id);
    }
}
