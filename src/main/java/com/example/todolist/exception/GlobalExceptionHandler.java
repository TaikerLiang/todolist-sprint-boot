package com.example.todolist.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage());

        // Check if it's an authentication-related exception
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (message.contains("User not found") ||
            message.contains("Invalid") ||
            message.contains("expired") ||
            message.contains("revoked")) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (message.contains("not found")) {
            status = HttpStatus.NOT_FOUND;
        } else if (message.contains("Forbidden") ||
                   message.contains("only revoke your own")) {
            status = HttpStatus.FORBIDDEN;
        }

        Map<String, Object> error = new HashMap<>();
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        error.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation exception: {}", ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Bad Request");
        error.put("message", "Validation failed");
        error.put("timestamp", Instant.now().toString());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        error.put("fields", fieldErrors);

        return ResponseEntity.badRequest().body(error);
    }
}
