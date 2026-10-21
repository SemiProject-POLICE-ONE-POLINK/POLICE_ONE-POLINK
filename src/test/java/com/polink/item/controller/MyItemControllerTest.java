package com.polink.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.polink.item.dto.ItemPageResponse;
import com.polink.item.service.ItemService;
import com.polink.user.entity.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * JWT 로그인 사용자 기준의 내 등록 물품 조회 Controller 테스트다.
 * 실제 JWT 서명 검증은 Spring Security가 담당하고,
 * 여기서는 Controller가 JWT의 sub·role을 올바르게 사용하는지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class MyItemControllerTest {

    // 실제 DB 조회 대신 반환값과 호출 여부를 제어할 가짜 Service다.
    @Mock
    private ItemService itemService;

    // 실제 로그인 토큰 대신 subject와 role Claim 값을 정할 수 있는 가짜 JWT다.
    @Mock
    private Jwt jwt;

    // 시민 JWT의 sub 값을 사용해 내 등록 물품 목록을 조회하는지 검사한다.
    @Test
    void citizenGetsOwnItemsUsingJwtSubject() {
        // JWT subject는 문자열 형태의 회원 번호다.
        given(jwt.getSubject()).willReturn("1");

        // JWT role Claim은 시민 역할로 설정한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.CITIZEN.name());

        // Service가 반환할 빈 첫 페이지 응답을 준비한다.
        ItemPageResponse serviceResponse = new ItemPageResponse(
                List.of(),
                0,
                10,
                0,
                0,
                true,
                true
        );

        // 사용자 ID=1, 첫 페이지, 페이지 크기 10으로 조회하면 위 응답을 반환한다.
        given(itemService.getMyItems(1, 0, 10))
                .willReturn(serviceResponse);

        // 실제 HTTP 요청 대신 Controller 메서드에 JWT를 직접 전달한다.
        ResponseEntity<ItemPageResponse> result = new MyItemController(itemService)
                .getMyItems(jwt, 0, 10);

        // 정상적인 시민 JWT 요청은 200 OK여야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Controller가 Service의 반환값을 그대로 응답 본문에 담아야 한다.
        assertEquals(serviceResponse, result.getBody());

        // JWT subject의 사용자 ID=1만 Service에 전달됐는지 확인한다.
        verify(itemService).getMyItems(1, 0, 10);
    }

    // 경찰 JWT는 시민 전용 내 등록 물품 조회를 사용할 수 없는지 검사한다.
    @Test
    void policeCannotGetCitizenMyItems() {
        // 경찰 계정의 JWT subject를 회원 번호 6으로 설정한다.
        given(jwt.getSubject()).willReturn("6");

        // role Claim을 경찰 역할로 설정한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.POLICE.name());

        // 시민 전용 API 호출 시 403 Forbidden 예외가 발생해야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MyItemController(itemService).getMyItems(jwt, 0, 10)
        );

        // 역할은 확인됐지만 시민이 아니므로 403이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검사에서 막혔으므로 DB 조회 Service는 호출되면 안 된다.
        verifyNoInteractions(itemService);
    }

    // Authorization Bearer 토큰 없이 요청하면 401인지 검사한다.
    @Test
    void requestWithoutJwtIsUnauthorized() {
        // JWT가 null이면 로그인 정보가 없으므로 401 예외가 발생해야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MyItemController(itemService).getMyItems(null, 0, 10)
        );

        // 인증되지 않은 요청은 401 Unauthorized여야 한다.
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        // 로그인 사용자 ID를 알 수 없으므로 Service는 호출되면 안 된다.
        verifyNoInteractions(itemService);
    }
}
