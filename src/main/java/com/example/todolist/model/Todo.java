package com.example.todolist.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "todolist")
public class Todo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    // UTC-safe creation timestamp
    @Column(nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();    

    // Constructors
    public Todo() {}
    public Todo(String title, String description) {
        this.title = title;
        this.description = description;
    }

}