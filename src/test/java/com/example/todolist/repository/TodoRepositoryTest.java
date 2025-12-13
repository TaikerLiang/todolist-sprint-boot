package com.example.todolist.repository;

import com.example.todolist.model.Todo;
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

    @Test
    void shouldSaveAndFindTodo() {
        // Arrange
        Todo todo = new Todo("Write tests", "Learn H2 with Spring Boot");

        // Act
        Todo saved = todoRepository.save(todo);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isCompleted()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Todo> found = todoRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Write tests");
    }

    @Test
    void shouldUpdateTodo() {
        // Arrange
        Todo todo = todoRepository.save(new Todo("Old title", "Old desc"));

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
        Todo todo = todoRepository.save(new Todo("Delete me", "Temp"));

        // Act
        todoRepository.deleteById(todo.getId());

        // Assert
        assertThat(todoRepository.findById(todo.getId())).isEmpty();
    }

    @Test
    void shouldEnforceNotNullConstraint() {
        // Arrange
        Todo todo = new Todo();
        todo.setDescription("Missing title");

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