package com.example.todolist.service;

import com.example.todolist.model.User;
import com.example.todolist.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public User createUser(User user) {
        // Validate username uniqueness
        userRepository.findByUsername(user.getUsername())
            .ifPresent(existing -> {
                throw new RuntimeException("Username already exists: " + user.getUsername());
            });

        return userRepository.save(user);
    }

    public User updateUser(Long id, User updatedUser) {
        return userRepository.findById(id)
            .map(user -> {
                // Check username uniqueness if username is being changed
                if (!user.getUsername().equals(updatedUser.getUsername())) {
                    userRepository.findByUsername(updatedUser.getUsername())
                        .ifPresent(existing -> {
                            throw new RuntimeException("Username already exists: " + updatedUser.getUsername());
                        });
                }

                user.setUsername(updatedUser.getUsername());
                user.setRole(updatedUser.getRole());
                return userRepository.save(user);
            })
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}
