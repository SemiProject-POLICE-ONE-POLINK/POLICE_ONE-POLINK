package com.polink.user.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE)
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
        String message = exception.getReason();

        if (message == null || message.isBlank()) {
            message = "요청을 처리하지 못했습니다.";
        }

        ErrorResponse response = new ErrorResponse(
                status,
                message
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    String defaultMessage = error.getDefaultMessage();

                    return defaultMessage == null || defaultMessage.isBlank()
                            ? "입력값을 확인해주세요."
                            : defaultMessage;
                })
                .findFirst()
                .orElse("입력값을 확인해주세요.");

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        400,
                        "요청 본문 형식을 확인해주세요."
                ));
    }
}
