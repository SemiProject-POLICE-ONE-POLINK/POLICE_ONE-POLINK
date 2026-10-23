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
import com.polink.item.returnrequest.dto.ReturnRequestRejectRequest;
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
import com.polink.item.returnrequest.dto.ReturnRequestCompleteResponse;
import java.time.LocalDateTime;
import com.polink.item.returnrequest.dto.ReturnRequestProcessResponse;
import com.polink.item.returnrequest.dto.MyReturnRequestListResponse;
import java.util.List;

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
        // given: 승인 권한을 가진 경찰관 JWT다.
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.POLICE.name());

        // Service가 승인 처리 후 반환할 응답을 준비한다.
        LocalDateTime processedAt = LocalDateTime.of(2026, 10, 22, 10, 0);
        ReturnRequestProcessResponse serviceResponse =
                new ReturnRequestProcessResponse(
                        1,
                        ReturnRequestStatus.APPROVED,
                        processedAt
                );

        when(returnRequestService.approveReturnRequest(2, 1))
                .thenReturn(serviceResponse);

        // when: 경찰이 1번 반환 신청을 승인한다.
        ResponseEntity<ReturnRequestProcessResponse> result =
                returnRequestController.approveReturnRequest(jwt, 1);

        // then: 명세에 따라 204가 아닌 200 OK를 반환해야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // 처리 결과 본문이 있어야 한다.
        ReturnRequestProcessResponse response = requireNonNull(result.getBody());
        assertEquals(1, response.requestId());
        assertEquals(ReturnRequestStatus.APPROVED, response.status());
        assertEquals(processedAt, response.processedAt());

        // JWT의 경찰 ID와 신청 번호가 Service에 전달되어야 한다.
        verify(returnRequestService).approveReturnRequest(2, 1);
    }

    @Test
    void 경찰은_반환_신청을_반려할_수_있다() {
        // given: 반려 권한을 가진 경찰관 JWT다.
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.POLICE.name());

        // 반려 처리가 끝난 뒤의 Service 응답이다.
        LocalDateTime processedAt = LocalDateTime.of(2026, 10, 22, 10, 5);
        ReturnRequestProcessResponse serviceResponse =
                new ReturnRequestProcessResponse(
                        1,
                        ReturnRequestStatus.REJECTED,
                        processedAt
                );

        when(returnRequestService.rejectReturnRequest(
                2,
                1,
                "신청자 정보가 일치하지 않습니다."
        )).thenReturn(serviceResponse);

        // when: 경찰이 반려 사유와 함께 신청을 반려한다.
        ReturnRequestRejectRequest request =
                new ReturnRequestRejectRequest("신청자 정보가 일치하지 않습니다.");

        ResponseEntity<ReturnRequestProcessResponse> result =
                returnRequestController.rejectReturnRequest(jwt, 1, request);

        // then: 200 OK와 반려 처리 결과를 반환해야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        ReturnRequestProcessResponse response = requireNonNull(result.getBody());
        assertEquals(1, response.requestId());
        assertEquals(ReturnRequestStatus.REJECTED, response.status());
        assertEquals(processedAt, response.processedAt());
    }

    @Test
    void 경찰은_반환_신청을_완료할_수_있다() {
        // given: 경찰 역할을 가진 로그인 토큰이다.
        when(jwt.getSubject()).thenReturn("2");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.POLICE.name());

        // Service가 실제 완료 시각을 포함한 결과를 반환한다고 가정한다.
        LocalDateTime completedAt = LocalDateTime.of(2026, 10, 21, 18, 0);
        ReturnRequestCompleteResponse serviceResponse =
                new ReturnRequestCompleteResponse(1, completedAt);

        when(returnRequestService.completeReturnRequest(2, 1))
                .thenReturn(serviceResponse);

        // when: 경찰이 1번 반환 신청의 물품 인계를 완료 처리한다.
        ResponseEntity<ReturnRequestCompleteResponse> result =
                returnRequestController.completeReturnRequest(jwt, 1);

        // then: API 명세대로 200 OK를 반환해야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // 응답 본문에는 완료 신청 번호와 완료 시각이 있어야 한다.
        ReturnRequestCompleteResponse response = requireNonNull(result.getBody());
        assertEquals(1, response.requestId());
        // 응답 JSON 필드명은 API 명세에 맞춰 returnedAt을 사용한다.
        assertEquals(completedAt, response.returnedAt());

        // Controller가 로그인한 경찰 번호와 신청 번호를 Service에 정확히 전달해야 한다.
        verify(returnRequestService).completeReturnRequest(2, 1);
    }

    @Test
    void 로그인하지_않으면_반환_완료_처리를_할_수_없다() {
        // when: JWT 없이 경찰 전용 완료 API를 호출한다.
        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> returnRequestController.completeReturnRequest(null, 1)
                );

        // then: 로그인하지 않은 요청은 401 Unauthorized여야 한다.
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        // 인증 검증에서 막혔으므로 Service는 호출되면 안 된다.
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void 시민은_반환_완료_처리를_할_수_없다() {
        // given: 시민 역할을 가진 로그인 토큰이다.
        when(jwt.getSubject()).thenReturn("1");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.CITIZEN.name());

        // when: 시민이 경찰 전용 완료 API를 호출한다.
        ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ResponseStatusException.class,
                        () -> returnRequestController.completeReturnRequest(jwt, 1)
                );

        // then: 역할 권한이 없으므로 403 Forbidden이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검증에서 막혔으므로 Service는 호출되면 안 된다.
        verifyNoInteractions(returnRequestService);
    }

    @Test
    void 시민은_상태별로_내_반환_신청_목록을_조회할_수_있다() {
        // given: 시민 1번의 로그인 토큰이다.
        when(jwt.getSubject()).thenReturn("1");
        when(jwt.getClaimAsString("role")).thenReturn(UserRole.CITIZEN.name());

        // Service가 프론트 페이지 UI용 메타데이터를 포함한 빈 목록을 반환한다고 가정한다.
        MyReturnRequestListResponse serviceResponse =
                new MyReturnRequestListResponse(
                        List.of(),
                        0,
                        10,
                        0,
                        0,
                        true,
                        true
                );

        // 시민 1번이 PENDING 상태로 필터링해 조회한 결과를 mock으로 준비한다.
        when(returnRequestService.getMyReturnRequests(
                1,
                ReturnRequestStatus.PENDING,
                0,
                10
        )).thenReturn(serviceResponse);

        // when: 시민이 PENDING 상태의 본인 반환 신청 목록을 조회한다.
        ResponseEntity<MyReturnRequestListResponse> result =
                returnRequestController.getMyReturnRequests(
                        jwt,
                        ReturnRequestStatus.PENDING,
                        0,
                        10
                );

        // then: 조회 성공 시 200 OK와 페이지 메타데이터를 반환해야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(0, requireNonNull(result.getBody()).page());

        // Controller가 로그인 사용자 ID와 상태 필터를 Service에 정확히 전달해야 한다.
        verify(returnRequestService).getMyReturnRequests(
                1,
                ReturnRequestStatus.PENDING,
                0,
                10
        );
    }
}
