package com.polink.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polink.item.dto.ItemPageResponse;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.lang.reflect.Method;
import org.springframework.web.bind.annotation.GetMapping;
import com.polink.item.exception.ItemExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/*
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
    // 실제 서버 없이 HTTP 요청과 JSON 오류 응답을 확인하는 테스트 도구다.
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MyItemController(itemService))
                // MockMvc는 실제 Spring Security 필터를 거치지 않으므로
                // @AuthenticationPrincipal Jwt 파라미터를 테스트에서 직접 처리한다.
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        // JWT 타입이면서 @AuthenticationPrincipal이 붙은 파라미터만 처리한다.
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                                && parameter.getParameterType().equals(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        // 이 테스트는 Authorization 헤더가 없는 상황이다.
                        // 따라서 Controller에 null JWT를 전달해 401 처리를 검증한다.
                        return null;
                    }
                })
                .setControllerAdvice(new ItemExceptionHandler())
                .build();
    }

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

    // 내 등록 물품 조회 API의 URL 경로가 명세대로 /my인지 검사한다.
    // URL이 바뀌면 프론트엔드와 API 문서가 함께 깨질 수 있으므로 경로 자체를 테스트한다.
    @Test
    void getMyItemsUsesMyEndpoint() throws NoSuchMethodException {
        // 선택 필터(itemType, status)를 포함한 실제 HTTP API 메서드를 찾는다.
        // 필터가 추가돼도 /my 경로 자체가 바뀌면 안 된다.
        Method method = MyItemController.class.getDeclaredMethod(
                "getMyItems",
                Jwt.class,
                ItemType.class,
                ItemStatus.class,
                int.class,
                int.class
        );

        // Controller 메서드에 선언된 @GetMapping 정보를 가져온다.
        GetMapping getMapping = method.getAnnotation(GetMapping.class);

        // 내 등록 물품 조회의 단일 경로가 /my인지 확인한다.
        assertArrayEquals(new String[]{"/my"}, getMapping.value());
    }

    // 토큰 없이 HTTP 요청하면 401과 안전한 공통 오류 형식이 반환되는지 검사한다.
    @Test
    void requestWithoutJwtReturnsSafeUnauthorizedResponse() throws Exception {
        // Authorization 헤더 없이 내 등록 물품 조회 URL을 호출한다.
        mockMvc.perform(get("/api/v1/items/my"))
                // 로그인 정보가 없으므로 401 Unauthorized여야 한다.
                .andExpect(status().isUnauthorized())
                // 공통 오류 응답에는 상태 코드와 사용자 안내 메시지가 포함된다.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                // 내부 클래스명·줄 번호가 담긴 trace는 절대 노출되면 안 된다.
                .andExpect(jsonPath("$.trace").doesNotExist());

        // 인증 단계에서 중단되므로 물품 조회 Service는 호출되면 안 된다.
        verifyNoInteractions(itemService);
    }
}
