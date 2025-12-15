package com.example.todolist.controller;

import com.example.todolist.model.Role;
import com.example.todolist.model.User;
import com.example.todolist.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.ailef.snapadmin.external.SnapAdminAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = { SnapAdminAutoConfiguration.class }
)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllUsers_shouldReturnUserList() throws Exception {
        // Arrange
        User user1 = new User("alice", Role.ADMIN);
        User user2 = new User("bob", Role.USER);

        when(userService.getAllUsers()).thenReturn(List.of(user1, user2));

        // Act & Assert
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].username").value("bob"));
    }

    @Test
    void getUserById_shouldReturnUser() throws Exception {
        // Arrange
        Long userId = 1L;
        User user = new User("alice", Role.ADMIN);
        user.setId(userId);

        when(userService.getUserById(userId)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void createUser_shouldReturnCreatedUser() throws Exception {
        // Arrange
        User request = new User("charlie", Role.MANAGER);

        User saved = new User("charlie", Role.MANAGER);
        saved.setId(1L);

        when(userService.createUser(any(User.class))).thenReturn(saved);

        // Act & Assert
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value("charlie"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        // Arrange
        Long userId = 1L;

        User updated = new User("robert", Role.ADMIN);
        updated.setId(userId);

        when(userService.updateUser(eq(userId), any(User.class)))
                .thenReturn(updated);

        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("robert"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void deleteUser_shouldReturnOk() throws Exception {
        // Arrange
        Long userId = 1L;
        doNothing().when(userService).deleteUser(userId);

        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().isOk());
    }
}
