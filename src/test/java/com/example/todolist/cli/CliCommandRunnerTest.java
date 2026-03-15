package com.example.todolist.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CliCommandRunnerTest {

    private ExitExecutor exitExecutor;
    private CliCommand helloCommand;

    @BeforeEach
    void setUp() {
        exitExecutor = mock(ExitExecutor.class);
        helloCommand = mock(CliCommand.class);
        when(helloCommand.getName()).thenReturn("hello");
    }

    @Test
    void noCommandFlag_doesNotExit() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments();
        runner.run(args);
        verifyNoInteractions(exitExecutor);
    }

    @Test
    void unknownCommand_exitsWithOne() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=unknown");
        runner.run(args);
        verify(exitExecutor).exit(1);
    }

    @Test
    void knownCommand_executesAndExitsWithZero() throws Exception {
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=hello");
        runner.run(args);
        verify(helloCommand).execute(args);
        verify(exitExecutor).exit(0);
    }

    @Test
    void commandThrowsException_exitsWithOne() throws Exception {
        doThrow(new RuntimeException("boom")).when(helloCommand).execute(any());
        var runner = new CliCommandRunner(List.of(helloCommand), exitExecutor);
        var args = new DefaultApplicationArguments("--command=hello");
        runner.run(args);
        verify(exitExecutor).exit(1);
    }

    @Test
    void duplicateCommandNames_throwsAtConstruction() {
        CliCommand duplicate = mock(CliCommand.class);
        when(duplicate.getName()).thenReturn("hello");
        assertThatThrownBy(() -> new CliCommandRunner(List.of(helloCommand, duplicate), exitExecutor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hello");
    }
}
