package com.example.todolist.cli.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatNoException;

class HelloCommandTest {

    private final HelloCommand command = new HelloCommand();

    @Test
    void getName_returnsHello() {
        assert command.getName().equals("hello");
    }

    @Test
    void execute_withName_doesNotThrow() throws Exception {
        var args = new DefaultApplicationArguments("--command=hello", "--name=Paul");
        assertThatNoException().isThrownBy(() -> command.execute(args));
    }

    @Test
    void execute_withoutName_doesNotThrow() throws Exception {
        var args = new DefaultApplicationArguments("--command=hello");
        assertThatNoException().isThrownBy(() -> command.execute(args));
    }
}
