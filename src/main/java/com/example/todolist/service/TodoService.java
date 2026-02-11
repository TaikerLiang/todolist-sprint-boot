package com.example.todolist.service;

import com.example.todolist.model.Todo;
import com.example.todolist.repository.TodoRepository;
import com.example.todolist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TodoService {
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;
    private final AuditCaptureService auditCaptureService;

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    @Transactional
    public Todo createTodo(Todo todo) {
        // Validate user exists
        if (todo.getUser() == null || todo.getUser().getId() == null) {
            throw new RuntimeException("User is required");
        }

        userRepository.findById(todo.getUser().getId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + todo.getUser().getId()));

        Todo savedTodo = todoRepository.save(todo);

        // Capture audit log for CREATE operation
        auditCaptureService.captureCreate("Todo", savedTodo.getId());

        return savedTodo;
    }

    @Transactional
    public Todo updateTodo(Long id, Todo updatedTodo) {
        return todoRepository.findById(id)
            .map(originalTodo -> {
                // Create a deep copy of original for audit comparison
                Todo beforeUpdate = cloneTodo(originalTodo);

                originalTodo.setTitle(updatedTodo.getTitle());
                originalTodo.setDescription(updatedTodo.getDescription());
                originalTodo.setCompleted(updatedTodo.isCompleted());

                // Validate and update user if provided
                if (updatedTodo.getUser() != null && updatedTodo.getUser().getId() != null) {
                    userRepository.findById(updatedTodo.getUser().getId())
                        .orElseThrow(() -> new RuntimeException("User not found with id: " + updatedTodo.getUser().getId()));
                    originalTodo.setUser(updatedTodo.getUser());
                }

                Todo savedTodo = todoRepository.save(originalTodo);

                // Capture audit log for UPDATE operation with before/after comparison
                auditCaptureService.captureUpdate("Todo", id, beforeUpdate, savedTodo);

                return savedTodo;
            })
            .orElseThrow(() -> new RuntimeException("Todo not found"));
    }

    /**
     * Helper method to clone a Todo for audit comparison.
     */
    private Todo cloneTodo(Todo todo) {
        Todo clone = new Todo();
        clone.setId(todo.getId());
        clone.setTitle(todo.getTitle());
        clone.setDescription(todo.getDescription());
        clone.setCompleted(todo.isCompleted());
        clone.setUser(todo.getUser());
        clone.setCreatedAt(todo.getCreatedAt());
        return clone;
    }

    @Transactional
    public void deleteTodo(Long id) {
        // Verify todo exists before deletion
        todoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Todo not found with id: " + id));

        // Delete the todo
        todoRepository.deleteById(id);

        // Capture audit log for DELETE operation AFTER deletion
        auditCaptureService.captureDelete("Todo", id);
    }
}