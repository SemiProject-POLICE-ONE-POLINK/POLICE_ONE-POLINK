package com.polink.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polink.item.dto.ItemMatchListResponse;
import com.polink.item.dto.ItemMatchRequest;
import com.polink.item.dto.ItemMatchResponse;
import com.polink.item.service.ItemMatchService;
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
import com.polink.item.exception.ItemExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * AI 물품 매칭 Controller의 JWT 인증·시민 권한·Service 연결을 검사한다.
 */
@ExtendWith(MockitoExtension.class)
class ItemMatchControllerTest {

    // 실제 후보 계산 대신 반환값과 호출 여부를 제어할 가짜 Service다.
    @Mock
    private ItemMatchService itemMatchService;

    // 실제 access token 대신 subject와 role Claim을 지정할 수 있는 가짜 JWT다.
    @Mock
    private Jwt jwt;

    // 실제 서버 없이 HTTP 상태 코드와 JSON 오류 응답을 확인하는 테스트 도구다.
    private MockMvc mockMvc;

    // Authorization 헤더가 있는 MockMvc 요청에 전달할 테스트용 시민 JWT다.
    private Jwt requestJwt;

    // 시민 JWT로 본인 분실물 후보 매칭을 요청하면 200인지 검사한다.
    @Test
    void citizenFindsItemMatches() {
        // JWT subject는 현재 로그인한 시민의 회원 번호다.
        given(jwt.getSubject()).willReturn("1");

        // 매칭 기능은 시민 전용이므로 CITIZEN 역할을 설정한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.CITIZEN.name());

        ItemMatchRequest request = new ItemMatchRequest("L-0001");

        // Service가 반환할 습득물 후보 한 건을 준비한다.
        ItemMatchListResponse serviceResponse = new ItemMatchListResponse(
                List.of(
                        new ItemMatchResponse(
                                "F-0001",
                                "카테고리가 일치합니다. 장소 정보가 유사합니다."
                        )
                )
        );

        // 시민 ID=1과 분실물 번호 L-0001로 조회하면 위 후보를 반환하도록 설정한다.
        given(itemMatchService.findMatches(1, "L-0001"))
                .willReturn(serviceResponse);

        ResponseEntity<ItemMatchListResponse> result =
                new ItemMatchController(itemMatchService)
                        .findMatches(jwt, request);

        // 정상적인 시민 요청은 200 OK여야 한다.
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // Controller는 Service가 반환한 후보 목록을 그대로 응답 본문에 담아야 한다.
        assertEquals(serviceResponse, result.getBody());

        // JWT subject의 회원 번호와 요청 분실물 번호가 Service로 전달됐는지 확인한다.
        verify(itemMatchService).findMatches(1, "L-0001");
    }

    // 경찰은 시민 전용 AI 매칭을 요청할 수 없는지 검사한다.
    @Test
    void policeCannotFindItemMatches() {
        // 로그인한 경찰관의 회원 번호를 설정한다.
        given(jwt.getSubject()).willReturn("6");

        // 경찰 역할로 요청하면 시민 권한 검사에서 차단되어야 한다.
        given(jwt.getClaimAsString("role"))
                .willReturn(UserRole.POLICE.name());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ItemMatchController(itemMatchService)
                        .findMatches(jwt, new ItemMatchRequest("L-0001"))
        );

        // 시민 전용 기능이므로 403 Forbidden이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검사에서 중단되므로 후보 매칭 Service는 호출되면 안 된다.
        verifyNoInteractions(itemMatchService);
    }

    // JWT 없이 요청하면 401인지 검사한다.
    @Test
    void requestWithoutJwtIsUnauthorized() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ItemMatchController(itemMatchService)
                        .findMatches(null, new ItemMatchRequest("L-0001"))
        );

        // 로그인하지 않은 요청은 401 Unauthorized여야 한다.
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        // 인증 단계에서 중단되므로 후보 매칭 Service는 호출되면 안 된다.
        verifyNoInteractions(itemMatchService);
    }

    // 각 테스트 전에 AI 매칭 Controller와 Item 공통 예외 처리기를 등록한다.
    @BeforeEach
    void setUp() {
        // Authorization 헤더가 있는 요청을 로그인 시민 요청으로 만들 테스트 JWT다.
        requestJwt = Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject("1")
                .claim("role", UserRole.CITIZEN.name())
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ItemMatchController(itemMatchService))
                // MockMvc는 실제 Spring Security 필터를 거치지 않으므로
                // @AuthenticationPrincipal Jwt 파라미터를 테스트에서 직접 처리한다.
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
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
                        // Authorization 헤더가 없으면 로그인하지 않은 요청이므로 null을 전달한다.
                        if (webRequest.getHeader("Authorization") == null) {
                            return null;
                        }

                        // 헤더가 있으면 테스트용 시민 JWT를 Controller에 전달한다.
                        return requestJwt;
                    }
                })
                // 400·401·403 예외를 내부 trace 없는 공통 JSON 응답으로 변환한다.
                .setControllerAdvice(new ItemExceptionHandler())
                .build();
    }

    // Authorization 헤더 없이 HTTP 요청하면 안전한 401 JSON 응답이 반환되는지 검사한다.
    @Test
    void requestWithoutAuthorizationHeaderReturnsSafeUnauthorizedResponse()
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/ai/item-matches")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "lostItemId": "L-0001"
                                        }
                                        """)
                )
                // 로그인 정보가 없으므로 401 Unauthorized여야 한다.
                .andExpect(status().isUnauthorized())
                // 공통 오류 응답에는 상태 코드와 안내 메시지만 포함한다.
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                // 내부 클래스명·줄 번호가 담긴 trace는 외부에 노출되면 안 된다.
                .andExpect(jsonPath("$.trace").doesNotExist());

        // 로그인 검증에서 중단되므로 후보 계산 Service는 호출되면 안 된다.
        verifyNoInteractions(itemMatchService);
    }

    // 분실물 번호가 빈 요청이면 Service 호출 전 400으로 처리되는지 검사한다.
    @Test
    void blankLostItemIdReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/ai/item-matches")
                                // 테스트용 시민 JWT를 전달하도록 Authorization 헤더를 넣는다.
                                .header("Authorization", "Bearer test-access-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "lostItemId": ""
                                        }
                                        """)
                )
                // @NotBlank 검증에 실패하므로 400 Bad Request여야 한다.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("분실물 번호는 필수입니다."))
                // 입력 검증 오류에도 내부 trace는 노출되면 안 된다.
                .andExpect(jsonPath("$.trace").doesNotExist());

        // 입력값 검증에서 중단되므로 후보 계산 Service는 호출되면 안 된다.
        verifyNoInteractions(itemMatchService);
    }
}