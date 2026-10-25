package com.polink.investigation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(
        basePackages = "com.polink.investigation.controller"
)
public class InvestigationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InvestigationExceptionHandler.class);

    // 잘못된 입력은 HTTP 400과 오류 문구로 응답
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                Map.of(
                        "status", 400,
                        "message", exception.getMessage()
                )
        );
    }

    // Service에서 지정한 오류 코드와 안내 문구를 그대로 응답
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatusException(
            ResponseStatusException exception
    ) {
        String message = exception.getReason();

        if (message == null || message.isBlank()) {
            message = "요청을 처리하지 못했습니다.";
        }

        // 서버·AI 처리 오류는 실패 위치와 원인을 서버 로그에 기록
        if (exception.getStatusCode().is5xxServerError()) {
            log.error(
                    "수사 API 처리 실패: status={}, message={}",
                    exception.getStatusCode().value(),
                    message,
                    exception
            );
        }

        // 별도 오류 페이지 처리 없이 현재 요청에서 JSON으로 반환
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of(
                        "status", exception.getStatusCode().value(),
                        "message", message
                ));
    }
}
