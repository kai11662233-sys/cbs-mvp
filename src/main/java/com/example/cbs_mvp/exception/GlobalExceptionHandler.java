package com.example.cbs_mvp.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        java.util.List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "VALIDATION_ERROR",
                "message", String.join(", ", errors),
                "details", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        // 400 Bad Request (Validation / Invalid Input)
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException ex) {
        // 409 Conflict (State mismatch)
        return buildResponse(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralError(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error (CorrelationId: {})", correlationId, ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Ref: " + correlationId);
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now(),
                "status", status.value(),
                "error", code,
                "message", message != null ? message : ""));
    }
}
