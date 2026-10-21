package com.polink.item.exception;

import com.polink.item.controller.ItemController;
import com.polink.item.controller.MyItemController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/*
 * ItemController와 MyItemController에서 발생한 예외를
 * 공통 JSON 오류 응답으로 변환한다.
 * 다른 도메인(회원·수사 등)의 예외 처리에는 영향을 주지 않는다.
 *
 * 기본 오류 응답의 내부 stack trace가 노출되지 않도록 막는다.
 */
@RestControllerAdvice(assignableTypes = {
        ItemController.class,
        MyItemController.class
})
public class ItemExceptionHandler {

    /*
     * 물품 API가 클라이언트에 반환하는 공통 오류 형식이다.
     * 내부 예외 클래스명·호출 경로는 포함하지 않는다.
     */
    public record ErrorResponse(
            int status,
            String message
    ) {
    }

    /*
     * @Valid 검증 실패 시 첫 번째 필드 오류 메시지를 400으로 반환한다.
     */
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

    /*
     * Service의 업무 규칙 위반(상태 오류, 중복 이미지 등)을 400으로 반환한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return badRequest(exception.getMessage());
    }

    /*
     * 존재하지 않는 물품 번호 요청은 404 Not Found로 반환한다.
     */
    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(
            ItemNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, exception.getMessage()));
    }

    /*
     * JSON 형식·날짜 형식이 올바르지 않은 요청은 400으로 반환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return badRequest("요청 형식 또는 날짜 형식을 확인해주세요.");
    }

    /*
     * 이미지 최대 용량(2MB)을 초과하면 413으로 반환한다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse(
                        413,
                        "이미지는 2MB 이하만 첨부할 수 있습니다."
                ));
    }

    /*
     * Controller의 인증·권한·상태 검증에서 발생한 401, 403, 404 예외를 처리한다.
     * 내부 stack trace 대신 상태 코드와 사용자 안내 메시지만 반환한다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception
    ) {
        String message = exception.getReason() == null
                ? "요청을 처리할 수 없습니다."
                : exception.getReason();

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ErrorResponse(
                        exception.getStatusCode().value(),
                        message
                ));
    }

    /*
     * 400 Bad Request 공통 응답을 만드는 보조 메서드다.
     */
    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message));
    }
}