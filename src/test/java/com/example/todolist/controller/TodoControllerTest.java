package com.example.todolist.controller;

import com.example.todolist.model.Todo;
import com.example.todolist.service.TodoService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllTodos_shouldReturnTodoList() throws Exception {
        // Arrange
        Todo todo1 = new Todo("Task 1", "Desc 1");
        Todo todo2 = new Todo("Task 2", "Desc 2");

        when(todoService.getAllTodos()).thenReturn(List.of(todo1, todo2));

        // Act & Assert
        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Task 1"))
                .andExpect(jsonPath("$[1].title").value("Task 2"));
    }

    @Test
    void createTodo_shouldReturnCreatedTodo() throws Exception {
        // Arrange
        Todo request = new Todo("New Task", "New Desc");

        Todo saved = new Todo("New Task", "New Desc");
        saved.setId(1L);

        when(todoService.createTodo(any(Todo.class))).thenReturn(saved);

        // Act & Assert
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("New Task"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void updateTodo_shouldReturnUpdatedTodo() throws Exception {
        // Arrange
        Long todoId = 1L;

        Todo updated = new Todo("Updated Task", "Updated Desc");
        updated.setCompleted(true);
        updated.setId(todoId);

        when(todoService.updateTodo(eq(todoId), any(Todo.class)))
                .thenReturn(updated);

        // Act & Assert
        mockMvc.perform(put("/api/todos/{id}", todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Task"))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void deleteTodo_shouldReturnOk() throws Exception {
        // Arrange
        Long todoId = 1L;
        doNothing().when(todoService).deleteTodo(todoId);

        // Act & Assert
        mockMvc.perform(delete("/api/todos/{id}", todoId))
                .andExpect(status().isOk());
    }
}