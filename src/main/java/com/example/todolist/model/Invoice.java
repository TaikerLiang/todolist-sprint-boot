package com.example.todolist.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "UUID")
    private UUID invoiceId = UUID.randomUUID();

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'CREATED'")
    private InvoiceStatus status = InvoiceStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, columnDefinition = "varchar(8) default 'MEDIUM'")
    private Level level = Level.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    // UTC-safe creation timestamp
    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    // Custom constructor
    public Invoice(UUID invoiceId, BigDecimal amount, User user) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.user = user;
    }

    // Constructor with status and level
    public Invoice(UUID invoiceId, BigDecimal amount, User user, InvoiceStatus status, Level level) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.user = user;
        this.status = status;
        this.level = level;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
