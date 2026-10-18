package com.polink.item.exception;

import com.polink.item.controller.ItemController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;


/**
 * ItemController에서 발생한 오류만 처리한다.
 * 다른 기능(회원·수사)의 예외 처리 방식에는 영향을 주지 않는다.
 */
@RestControllerAdvice(assignableTypes = ItemController.class)
public class ItemExceptionHandler {

    /**
     * 물품 기능에서 사용하는 오류 응답 형식이다.
     */
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
                    return defaultMessage != null
                            ? defaultMessage
                            : "입력값을 확인해주세요.";
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

    /**
     * 존재하지 않는 물품 상세 조회는 HTTP 404로 응답한다.
     */
    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(
            ItemNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return badRequest("요청 형식 또는 날짜 형식을 확인해주세요.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse(413, "이미지는 2MB 이하만 첨부할 수 있습니다."));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
    }

}