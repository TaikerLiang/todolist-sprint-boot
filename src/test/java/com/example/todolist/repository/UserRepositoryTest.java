package com.example.todolist.repository;

import com.example.todolist.model.Role;
import com.example.todolist.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {
        // Arrange
        User user = new User("johndoe", Role.USER);

        // Act
        User saved = userRepository.save(user);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("johndoe");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("johndoe");
    }

    @Test
    void shouldFindUserByUsername() {
        // Arrange
        User user = userRepository.save(new User("alice", Role.ADMIN));

        // Act
        Optional<User> found = userRepository.findByUsername("alice");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
        assertThat(found.get().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldReturnEmptyForNonExistentUsername() {
        // Act
        Optional<User> found = userRepository.findByUsername("nonexistent");

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateUser() {
        // Arrange
        User user = userRepository.save(new User("bob", Role.USER));

        // Act
        user.setUsername("robert");
        user.setRole(Role.MANAGER);
        User updated = userRepository.save(user);

        // Assert
        assertThat(updated.getUsername()).isEqualTo("robert");
        assertThat(updated.getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    void shouldDeleteUser() {
        // Arrange
        User user = userRepository.save(new User("temp", Role.USER));

        // Act
        userRepository.deleteById(user.getId());

        // Assert
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void shouldEnforceUsernameNotNull() {
        // Arrange
        User user = new User();
        user.setRole(Role.USER);

        // Act & Assert
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                userRepository.save(user);
                userRepository.flush();
            }
        )).isNotNull();
    }

    @Test
    void shouldEnforceUsernameUnique() {
        // Arrange
        userRepository.save(new User("duplicate", Role.USER));
        User duplicate = new User("duplicate", Role.ADMIN);

        // Act & Assert
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                userRepository.save(duplicate);
                userRepository.flush();
            }
        )).isNotNull();
    }
}
