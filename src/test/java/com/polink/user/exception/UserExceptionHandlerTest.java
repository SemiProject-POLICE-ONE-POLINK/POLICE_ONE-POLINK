package com.polink.user.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserExceptionHandlerTest {

    private final UserExceptionHandler handler =
            new UserExceptionHandler();

    @Test
    void validationErrorReturnsCommonResponse() {
        MethodArgumentNotValidException exception =
                mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError(
                        "request",
                        "userName",
                        "아이디를 입력해주세요."
                )
        ));

        var response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                new UserExceptionHandler.ErrorResponse(
                        400,
                        "아이디를 입력해주세요."
                ),
                response.getBody()
        );
    }

    @Test
    void unreadableMessageReturnsCommonResponse() {
        var response = handler.handleUnreadableMessage();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                new UserExceptionHandler.ErrorResponse(
                        400,
                        "요청 본문 형식을 확인해주세요."
                ),
                response.getBody()
        );
    }

    @Test
    void responseStatusReturnsCommonResponse() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "회원을 찾을 수 없습니다."
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(
                new UserExceptionHandler.ErrorResponse(
                        404,
                        "회원을 찾을 수 없습니다."
                ),
                response.getBody()
        );
    }

    @Test
    void responseStatusWithoutReasonUsesFallbackMessage() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.BAD_REQUEST)
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                new UserExceptionHandler.ErrorResponse(
                        400,
                        "요청을 처리하지 못했습니다."
                ),
                response.getBody()
        );
    }
}