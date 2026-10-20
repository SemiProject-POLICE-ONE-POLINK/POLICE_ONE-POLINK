package com.polink.item.exception;

import com.polink.item.controller.LocalReturnRequestController;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 로컬 반환 신청 API에서 발생한 입력·상태 오류를 처리한다.
 * 다른 도메인과 실제 로그인 기능에는 영향을 주지 않는다.
 */
@RestControllerAdvice(assignableTypes = LocalReturnRequestController.class)
@Profile("local")
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

                    return defaultMessage == null
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return badRequest("요청 본문 형식을 확인해주세요.");
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message));
    }
}
