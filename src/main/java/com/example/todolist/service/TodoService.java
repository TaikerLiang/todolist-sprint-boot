package com.example.todolist.service;

import com.example.todolist.model.Todo;
import com.example.todolist.repository.TodoRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TodoService {
    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public Todo createTodo(Todo todo) {
        return todoRepository.save(todo);
    }

    public Todo updateTodo(Long id, Todo updatedTodo) {
        return todoRepository.findById(id)
            .map(todo -> {
                todo.setTitle(updatedTodo.getTitle());
                todo.setDescription(updatedTodo.getDescription());
                todo.setCompleted(updatedTodo.isCompleted());
                return todoRepository.save(todo);
            })
            .orElseThrow(() -> new RuntimeException("Todo not found"));
    }

    public void deleteTodo(Long id) {
        todoRepository.deleteById(id);
    }
}