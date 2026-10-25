package com.polink.user.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(
        basePackages = "com.polink.user.controller"
)
public class UserExceptionHandler {

    public record ErrorResponse(int status, String message) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception) {

        int status = exception.getStatusCode().value();

        ErrorResponse response = new ErrorResponse(
                status,
                exception.getReason()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("입력값을 확인해주세요.");

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message));
    }
}
