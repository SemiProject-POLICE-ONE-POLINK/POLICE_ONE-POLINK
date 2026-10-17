package com.polink.investigation.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(
        basePackages = "com.polink.investigation.controller"
)
public class InvestigationExceptionHandler {

    // 잘못된 입력은 HTTP 400과 오류 문구로 응답
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                Map.of("message", exception.getMessage())
        );
    }
}
