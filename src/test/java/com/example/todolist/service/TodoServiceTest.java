package com.example.todolist.service;

import com.example.todolist.model.Role;
import com.example.todolist.model.Todo;
import com.example.todolist.model.User;
import com.example.todolist.repository.TodoRepository;
import com.example.todolist.repository.UserRepository;
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

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TodoService todoService;

    @Test
    void getAllTodos_shouldReturnAllTodos() {
        // Arrange
        User user = new User("testuser", Role.USER);
        user.setId(1L);

        Todo todo1 = new Todo("Task 1", "Desc 1", user);
        Todo todo2 = new Todo("Task 2", "Desc 2", user);

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
        User user = new User("testuser", Role.USER);
        user.setId(1L);

        Todo todo = new Todo("New Task", "New Desc", user);

        Todo savedTodo = new Todo("New Task", "New Desc", user);
        savedTodo.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(todoRepository.save(any(Todo.class))).thenReturn(savedTodo);

        // Act
        Todo result = todoService.createTodo(todo);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("New Task");
        assertThat(result.isCompleted()).isFalse();
        assertThat(result.getUser()).isNotNull();

        verify(userRepository).findById(1L);
        verify(todoRepository).save(todo);
    }

    @Test
    void updateTodo_shouldUpdateExistingTodo() {
        // Arrange
        Long todoId = 1L;

        User user = new User("testuser", Role.USER);
        user.setId(1L);

        Todo existing = new Todo("Old Title", "Old Desc", user);
        existing.setId(todoId);

        Todo updated = new Todo("New Title", "New Desc", user);
        updated.setCompleted(true);

        when(todoRepository.findById(todoId)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(todoRepository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Todo result = todoService.updateTodo(todoId, updated);

        // Assert
        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("New Desc");
        assertThat(result.isCompleted()).isTrue();

        verify(todoRepository).findById(todoId);
        verify(userRepository).findById(1L);
        verify(todoRepository).save(existing);
    }

    @Test
    void updateTodo_shouldThrowException_whenTodoNotFound() {
        // Arrange
        Long todoId = 99L;

        User user = new User("testuser", Role.USER);
        user.setId(1L);

        Todo updated = new Todo("Doesn't matter", "Nope", user);

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

    @Test
    void createTodo_shouldThrowException_whenUserNotFound() {
        // Arrange
        User user = new User("testuser", Role.USER);
        user.setId(99L);

        Todo todo = new Todo("Task", "Desc", user);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> todoService.createTodo(todo))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found with id: 99");

        verify(userRepository).findById(99L);
        verify(todoRepository, never()).save(any());
    }

    @Test
    void createTodo_shouldThrowException_whenUserIsNull() {
        // Arrange
        Todo todo = new Todo("Task", "Desc", null);

        // Act & Assert
        assertThatThrownBy(() -> todoService.createTodo(todo))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User is required");

        verify(userRepository, never()).findById(any());
        verify(todoRepository, never()).save(any());
    }
}