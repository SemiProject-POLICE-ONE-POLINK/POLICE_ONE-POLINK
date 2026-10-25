package com.polink.item.exception;

import com.polink.item.controller.ReturnRequestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 반환 신청 API에서 발생한 입력·상태 오류를 처리한다.
 * 실행 프로필과 관계없이 같은 API 오류 형식을 반환해야 하므로
 * local 프로필로 제한하지 않는다.
 */

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReturnRequestController.class)
public class ReturnRequestExceptionHandler {

    public record ErrorResponse(int status, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
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

        return badRequest(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return badRequest(exception.getMessage());
    }

    /** 요청한 반환 신청 또는 연결 물품이 없으면 404를 반환한다. */
    @ExceptionHandler(ReturnRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ReturnRequestNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, exception.getMessage()));
    }

    /** 중복 신청이나 잘못된 상태 전이는 입력 형식 오류가 아니라 409 충돌이다. */
    @ExceptionHandler(ReturnRequestConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ReturnRequestConflictException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return badRequest("요청 본문 형식을 확인해주세요.");
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        String resolvedMessage =
                message == null || message.isBlank()
                        ? "입력값을 확인해주세요."
                        : message;

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, resolvedMessage));
    }

    /*
     * Controller가 권한·인증 검증 중 발생시키는 401, 403 예외를
     * 필요한 상태 코드와 메시지만 담은 응답으로 변환한다.
     * 기본 오류 응답의 내부 stack trace 노출을 막는다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception
    ) {
        String message = exception.getReason();

        if (message == null || message.isBlank()) {
            message = "요청을 처리하지 못했습니다.";
        }

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ErrorResponse(
                        exception.getStatusCode().value(),
                        message
                ));
    }
}
