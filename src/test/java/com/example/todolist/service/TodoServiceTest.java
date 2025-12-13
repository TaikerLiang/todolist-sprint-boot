package com.example.todolist.service;

import com.example.todolist.model.Todo;
import com.example.todolist.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    @Test
    void getAllTodos_shouldReturnAllTodos() {
        // Arrange
        Todo todo1 = new Todo("Task 1", "Desc 1");
        Todo todo2 = new Todo("Task 2", "Desc 2");

        when(todoRepository.findAll()).thenReturn(List.of(todo1, todo2));

        // Act
        List<Todo> result = todoService.getAllTodos();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Todo::getTitle)
                .containsExactly("Task 1", "Task 2");

        verify(todoRepository).findAll();
    }

    @Test
    void createTodo_shouldSaveAndReturnTodo() {
        // Arrange
        Todo todo = new Todo("New Task", "New Desc");

        Todo savedTodo = new Todo("New Task", "New Desc");
        savedTodo.setId(1L);

        when(todoRepository.save(any(Todo.class))).thenReturn(savedTodo);

        // Act
        Todo result = todoService.createTodo(todo);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("New Task");
        assertThat(result.isCompleted()).isFalse();

        verify(todoRepository).save(todo);
    }

    @Test
    void updateTodo_shouldUpdateExistingTodo() {
        // Arrange
        Long todoId = 1L;

        Todo existing = new Todo("Old Title", "Old Desc");
        existing.setId(todoId);

        Todo updated = new Todo("New Title", "New Desc");
        updated.setCompleted(true);

        when(todoRepository.findById(todoId)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.updateTodo(todoId, updated);

        // Assert
        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("New Desc");
        assertThat(result.isCompleted()).isTrue();

        verify(todoRepository).findById(todoId);
        verify(todoRepository).save(existing);
    }

    @Test
    void updateTodo_shouldThrowException_whenTodoNotFound() {
        // Arrange
        Long todoId = 99L;
        Todo updated = new Todo("Doesn't matter", "Nope");

        when(todoRepository.findById(todoId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> todoService.updateTodo(todoId, updated))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Todo not found");

        verify(todoRepository).findById(todoId);
        verify(todoRepository, never()).save(any());
    }

    @Test
    void deleteTodo_shouldDeleteById() {
        // Arrange
        Long todoId = 1L;

        doNothing().when(todoRepository).deleteById(todoId);

        // Act
        todoService.deleteTodo(todoId);

        // Assert
        verify(todoRepository).deleteById(todoId);
    }
}