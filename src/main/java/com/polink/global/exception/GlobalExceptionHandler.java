package com.polink.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(
            int status,
            String message
    ) {
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception
    ) {
        log.error("처리되지 않은 API 예외가 발생했습니다.", exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        500,
                        "서버에서 요청을 처리하지 못했습니다."
                ));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        return new ResponseEntity<>(
                new ErrorResponse(
                        statusCode.value(),
                        resolveMessage(statusCode)
                ),
                headers,
                statusCode
        );
    }

    private String resolveMessage(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> "요청값 또는 요청 형식을 확인해주세요.";
            case 404 -> "요청한 API를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 HTTP 메서드입니다.";
            case 406 -> "지원하지 않는 응답 형식입니다.";
            case 413 -> "첨부 파일 용량을 확인해주세요.";
            case 415 -> "지원하지 않는 Content-Type입니다.";
            default -> statusCode.is5xxServerError()
                    ? "서버에서 요청을 처리하지 못했습니다."
                    : "요청을 처리하지 못했습니다.";
        };
    }
}