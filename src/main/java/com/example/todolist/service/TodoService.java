package com.example.todolist.service;

import com.example.todolist.model.Todo;
import com.example.todolist.repository.TodoRepository;
import com.example.todolist.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TodoService {
    private final TodoRepository todoRepository;
    private final UserRepository userRepository;

    public TodoService(TodoRepository todoRepository, UserRepository userRepository) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
    }

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public Todo createTodo(Todo todo) {
        // Validate user exists
        if (todo.getUser() == null || todo.getUser().getId() == null) {
            throw new RuntimeException("User is required");
        }

        userRepository.findById(todo.getUser().getId())
            .orElseThrow(() -> new RuntimeException("User not found with id: " + todo.getUser().getId()));

        return todoRepository.save(todo);
    }

    public Todo updateTodo(Long id, Todo updatedTodo) {
        return todoRepository.findById(id)
            .map(todo -> {
                todo.setTitle(updatedTodo.getTitle());
                todo.setDescription(updatedTodo.getDescription());
                todo.setCompleted(updatedTodo.isCompleted());

                // Validate and update user if provided
                if (updatedTodo.getUser() != null && updatedTodo.getUser().getId() != null) {
                    userRepository.findById(updatedTodo.getUser().getId())
                        .orElseThrow(() -> new RuntimeException("User not found with id: " + updatedTodo.getUser().getId()));
                    todo.setUser(updatedTodo.getUser());
                }

                return todoRepository.save(todo);
            })
            .orElseThrow(() -> new RuntimeException("Todo not found"));
    }

    public void deleteTodo(Long id) {
        todoRepository.deleteById(id);
    }
}