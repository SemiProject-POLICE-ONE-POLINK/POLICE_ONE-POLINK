package com.polink.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static java.util.Objects.requireNonNull;

import com.polink.item.returnrequest.dto.ReturnRequestCreateRequest;
import com.polink.item.returnrequest.dto.ReturnRequestCreateResponse;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.service.ReturnRequestService;
import com.polink.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReturnRequestControllerTest {

    // 실제 DB를 사용하지 않고 Service 호출 여부만 검증하기 위한 가짜 객체다.
    @Mock
    private ReturnRequestService returnRequestService;

    // 로그인한 사용자의 JWT 정보를 흉내 내기 위한 가짜 토큰이다.
    @Mock
    private Jwt jwt;

    private ReturnRequestController returnRequestController;

    @BeforeEach
    void setUp() {
        // 테스트마다 동일한 Service Mock을 사용하는 Controller를 새로 생성한다.
        returnRequestController = new ReturnRequestController(returnRequestService);
    }

    @Test
    void 시민은_반환_신청을_등록할_수_있다() {
        // given: JWT의 sub는 회원 ID, role은 시민 역할이다.
        when(jwt.getSubject()).thenReturn("1");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.CITIZEN.name());

        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest("L-0001", "F-0001", "습득물 반환을 신청합니다.");

        ReturnRequestCreateResponse serviceResponse =
                new ReturnRequestCreateResponse(1, ReturnRequestStatus.PENDING);

        // Service가 반환할 성공 결과를 미리 정의한다.
        when(returnRequestService.createReturnRequest(1, request))
                .thenReturn(serviceResponse);

        // when: 시민이 반환 신청 등록 API를 호출한다.
        ResponseEntity<ReturnRequestCreateResponse> result =
                returnRequestController.createReturnRequest(jwt, request);

        // then: 새 자원이 생성됐으므로 201 Created를 반환한다.
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        // 201 Created 응답에는 새로 생성된 반환 신청 정보가 있어야 한다.
        ReturnRequestCreateResponse createdReturnRequest =
                requireNonNull(result.getBody());
        assertEquals(1, createdReturnRequest.requestId());

        // Controller가 JWT의 회원 ID를 Service에 전달했는지 검증한다.
        verify(returnRequestService).createReturnRequest(1, request);
    }

    @Test
    void 경찰은_반환_신청을_등록할_수_없다() {
        // given: 경찰 토큰으로 시민 전용 API를 호출하는 상황이다.
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.POLICE.name());

        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest("L-0001", "F-0001", "반환 신청");

        // when: 경찰이 반환 신청을 시도한다.
        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> returnRequestController.createReturnRequest(jwt, request)
                );

        // then: 권한 부족이므로 403 Forbidden이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검증에서 막혔으므로 Service는 호출되면 안 된다.
        verify(returnRequestService, never()).createReturnRequest(any(), any());
    }

    @Test
    void 로그인하지_않으면_반환_신청을_등록할_수_없다() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest("L-0001", "F-0001", "반환 신청");

        // when: JWT 없이 요청한다.
        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> returnRequestController.createReturnRequest(null, request)
                );

        // then: 인증 정보가 없으므로 401 Unauthorized여야 한다.
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        // 로그인 검증에서 막혔으므로 Service가 호출되면 안 된다.
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void 경찰은_반환_신청을_승인할_수_있다() {
        // given: 경찰 ID 2번의 정상 JWT다.
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.POLICE.name());

        // when: 경찰이 1번 반환 신청을 승인한다.
        ResponseEntity<Void> result =
                returnRequestController.approveReturnRequest(jwt, 1);

        // then: 승인 성공 시 본문 없이 204 No Content를 반환한다.
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());

        // JWT의 경찰 ID와 반환 신청 번호가 Service에 정확히 전달되어야 한다.
        verify(returnRequestService).approveReturnRequest(2, 1);
    }
}
