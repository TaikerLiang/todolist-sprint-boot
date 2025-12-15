package com.example.todolist.service;

import com.example.todolist.model.Role;
import com.example.todolist.model.User;
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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        // Arrange
        User user1 = new User("alice", Role.ADMIN);
        User user2 = new User("bob", Role.USER);

        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        // Act
        List<User> result = userService.getAllUsers();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getUsername)
                .containsExactly("alice", "bob");

        verify(userRepository).findAll();
    }

    @Test
    void getUserById_shouldReturnUser_whenUserExists() {
        // Arrange
        Long userId = 1L;
        User user = new User("alice", Role.ADMIN);
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        User result = userService.getUserById(userId);

        // Assert
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getUsername()).isEqualTo("alice");
        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_shouldThrowException_whenUserNotFound() {
        // Arrange
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found with id: 99");

        verify(userRepository).findById(userId);
    }

    @Test
    void createUser_shouldSaveAndReturnUser() {
        // Arrange
        User user = new User("charlie", Role.MANAGER);

        User savedUser = new User("charlie", Role.MANAGER);
        savedUser.setId(1L);

        when(userRepository.findByUsername("charlie")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = userService.createUser(user);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("charlie");
        assertThat(result.getRole()).isEqualTo(Role.MANAGER);

        verify(userRepository).findByUsername("charlie");
        verify(userRepository).save(user);
    }

    @Test
    void createUser_shouldThrowException_whenUsernameExists() {
        // Arrange
        User existing = new User("alice", Role.ADMIN);
        existing.setId(1L);

        User newUser = new User("alice", Role.USER);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(newUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists: alice");

        verify(userRepository).findByUsername("alice");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldUpdateExistingUser() {
        // Arrange
        Long userId = 1L;

        User existing = new User("bob", Role.USER);
        existing.setId(userId);

        User updated = new User("robert", Role.MANAGER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("robert")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateUser(userId, updated);

        // Assert
        assertThat(result.getUsername()).isEqualTo("robert");
        assertThat(result.getRole()).isEqualTo(Role.MANAGER);

        verify(userRepository).findById(userId);
        verify(userRepository).findByUsername("robert");
        verify(userRepository).save(existing);
    }

    @Test
    void updateUser_shouldNotCheckUsernameUniqueness_whenUsernameUnchanged() {
        // Arrange
        Long userId = 1L;

        User existing = new User("alice", Role.USER);
        existing.setId(userId);

        User updated = new User("alice", Role.ADMIN); // Same username, different role

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User result = userService.updateUser(userId, updated);

        // Assert
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).findByUsername(any()); // Should not check
        verify(userRepository).save(existing);
    }

    @Test
    void updateUser_shouldThrowException_whenUsernameExists() {
        // Arrange
        Long userId = 1L;

        User existing = new User("bob", Role.USER);
        existing.setId(userId);

        User updated = new User("alice", Role.MANAGER);

        User conflicting = new User("alice", Role.ADMIN);
        conflicting.setId(2L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(conflicting));

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(userId, updated))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Username already exists: alice");

        verify(userRepository).findById(userId);
        verify(userRepository).findByUsername("alice");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldThrowException_whenUserNotFound() {
        // Arrange
        Long userId = 99L;
        User updated = new User("nobody", Role.USER);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(userId, updated))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found with id: 99");

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_shouldDeleteById() {
        // Arrange
        Long userId = 1L;

        when(userRepository.existsById(userId)).thenReturn(true);
        doNothing().when(userRepository).deleteById(userId);

        // Act
        userService.deleteUser(userId);

        // Assert
        verify(userRepository).existsById(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_shouldThrowException_whenUserNotFound() {
        // Arrange
        Long userId = 99L;

        when(userRepository.existsById(userId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found with id: 99");

        verify(userRepository).existsById(userId);
        verify(userRepository, never()).deleteById(any());
    }
}
