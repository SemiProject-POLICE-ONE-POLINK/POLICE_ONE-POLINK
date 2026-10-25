package com.polink.investigation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestigationExceptionHandlerTest {

    private final InvestigationExceptionHandler handler =
            new InvestigationExceptionHandler();

    @Test
    void validationErrorReturnsCommonResponse() {
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError(
                        "request",
                        "content",
                        "수사 내용을 입력해주세요."
                )
        ));

        var response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                Map.of(
                        "status", 400,
                        "message", "수사 내용을 입력해주세요."
                ),
                response.getBody()
        );
    }

    @Test
    void unreadableMessageReturnsCommonResponse() {
        var response = handler.handleUnreadableMessage();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                Map.of(
                        "status", 400,
                        "message", "요청 본문 형식을 확인해주세요."
                ),
                response.getBody()
        );
    }

    @Test
    void illegalArgumentReturnsCommonResponse() {
        var response = handler.handleInvalidInput(
                new IllegalArgumentException("입력값이 올바르지 않습니다.")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                Map.of(
                        "status", 400,
                        "message", "입력값이 올바르지 않습니다."
                ),
                response.getBody()
        );
    }

    @Test
    void responseStatusReturnsCommonResponse() {
        var response = handler.handleStatusException(
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "수사 자료를 조회할 권한이 없습니다."
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of(
                        "status", 403,
                        "message", "수사 자료를 조회할 권한이 없습니다."
                ),
                response.getBody()
        );
    }
}