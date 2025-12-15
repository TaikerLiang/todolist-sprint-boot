package com.example.todolist.repository;

import com.example.todolist.model.Role;
import com.example.todolist.model.Todo;
import com.example.todolist.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindTodo() {
        // Arrange
        User user = userRepository.save(new User("testuser", Role.USER));
        Todo todo = new Todo("Write tests", "Learn H2 with Spring Boot", user);

        // Act
        Todo saved = todoRepository.save(todo);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isCompleted()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUser()).isNotNull();
        assertThat(saved.getUser().getUsername()).isEqualTo("testuser");

        Optional<Todo> found = todoRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Write tests");
    }

    @Test
    void shouldUpdateTodo() {
        // Arrange
        User user = userRepository.save(new User("testuser", Role.USER));
        Todo todo = todoRepository.save(new Todo("Old title", "Old desc", user));

        // Act
        todo.setTitle("New title");
        todo.setCompleted(true);
        Todo updated = todoRepository.save(todo);

        // Assert
        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.isCompleted()).isTrue();
    }

    @Test
    void shouldDeleteTodo() {
        // Arrange
        User user = userRepository.save(new User("testuser", Role.USER));
        Todo todo = todoRepository.save(new Todo("Delete me", "Temp", user));

        // Act
        todoRepository.deleteById(todo.getId());

        // Assert
        assertThat(todoRepository.findById(todo.getId())).isEmpty();
    }

    @Test
    void shouldEnforceNotNullConstraint() {
        // Arrange
        User user = userRepository.save(new User("testuser", Role.USER));
        Todo todo = new Todo();
        todo.setDescription("Missing title");
        todo.setUser(user);

        // Act & Assert
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                todoRepository.save(todo);
                todoRepository.flush();
            }
        )).isNotNull();
    }

    @Test
    void shouldEnforceUserNotNull() {
        // Arrange
        Todo todo = new Todo();
        todo.setTitle("Missing user");

        // Act & Assert
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                todoRepository.save(todo);
                todoRepository.flush();
            }
        )).isNotNull();
    }
}