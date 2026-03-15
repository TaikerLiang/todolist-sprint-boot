package com.example.todolist.controller;

import com.example.todolist.cli.CliCommand;
import com.example.todolist.cli.job.HelloCommand;
import com.example.todolist.config.SecurityConfig;
import com.example.todolist.security.JwtAuthenticationEntryPoint;
import com.example.todolist.security.JwtAuthenticationFilter;
import com.example.todolist.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tech.ailef.snapadmin.external.SnapAdminAutoConfiguration;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = JobController.class,
    excludeAutoConfiguration = {SnapAdminAutoConfiguration.class}
)
@Import(SecurityConfig.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HelloCommand helloCommand;  // replaces the real HelloCommand; Spring injects as List<CliCommand>=[helloCommand]

    @MockBean
    private ApplicationArguments applicationArguments;  // required — not auto-provided in WebMvcTest slice

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUpFilter() throws Exception {
        // The mock JwtAuthenticationFilter must forward requests through the chain;
        // without this, the filter swallows every request and returns blank 200.
        doAnswer(invocation -> {
            ServletRequest req = invocation.getArgument(0);
            ServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_knownCommand_returns200() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");

        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isOk());

        verify(helloCommand).execute(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_unknownCommand_returns404() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");

        mockMvc.perform(post("/admin/jobs/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown command: unknown"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void runJob_withUserRole_returns403() throws Exception {
        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isForbidden());
    }

    @Test
    void runJob_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void runJob_commandThrowsException_returns500() throws Exception {
        when(helloCommand.getName()).thenReturn("hello");
        doThrow(new RuntimeException("boom")).when(helloCommand).execute(any());

        mockMvc.perform(post("/admin/jobs/hello"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Command failed"));
    }
}
