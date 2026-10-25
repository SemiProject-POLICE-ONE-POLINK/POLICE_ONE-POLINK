package com.polink.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private final WebRequest request = mock(WebRequest.class);

    @Test
    void typeMismatchReturnsCommonBadRequest() throws Exception {
        var response = handler.handleException(
                new TypeMismatchException("wrong", Integer.class),
                request
        );

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                new GlobalExceptionHandler.ErrorResponse(
                        400,
                        "요청값 또는 요청 형식을 확인해주세요."
                ),
                response.getBody()
        );
    }

    @Test
    void unsupportedMethodReturnsCommonResponse() throws Exception {
        var response = handler.handleException(
                new HttpRequestMethodNotSupportedException("TRACE"),
                request
        );

        assertNotNull(response);
        assertEquals(
                HttpStatus.METHOD_NOT_ALLOWED,
                response.getStatusCode()
        );
        assertEquals(
                new GlobalExceptionHandler.ErrorResponse(
                        405,
                        "지원하지 않는 HTTP 메서드입니다."
                ),
                response.getBody()
        );
    }

    @Test
    void unknownApiReturnsCommonNotFound() throws Exception {
        var response = handler.handleException(
                new NoResourceFoundException(
                        HttpMethod.GET,
                        "/api/v1/unknown",
                        "api/v1/unknown"
                ),
                request
        );

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(
                new GlobalExceptionHandler.ErrorResponse(
                        404,
                        "요청한 API를 찾을 수 없습니다."
                ),
                response.getBody()
        );
    }

    @Test
    void unexpectedExceptionReturnsCommonServerError() {
        var response = handler.handleUnexpected(
                new IllegalStateException("내부 처리 실패")
        );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );
        assertEquals(
                new GlobalExceptionHandler.ErrorResponse(
                        500,
                        "서버에서 요청을 처리하지 못했습니다."
                ),
                response.getBody()
        );
    }
}