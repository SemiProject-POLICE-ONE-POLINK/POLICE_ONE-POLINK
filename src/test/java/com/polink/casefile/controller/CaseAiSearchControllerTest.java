package com.polink.casefile.controller;

import com.polink.casefile.dto.request.SimilarCaseSearchRequest;
import com.polink.casefile.dto.response.SimilarCaseResponse;
import com.polink.casefile.dto.response.SimilarCaseSearchResponse;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.exception.CaseExceptionHandler;
import com.polink.casefile.service.SimilarCaseSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaseAiSearchControllerTest {

    @Mock
    private SimilarCaseSearchService similarCaseSearchService;

    private MockMvc mockMvc;
    private Jwt requestJwt;

    @BeforeEach
    void setUp() {
        requestJwt = createJwt("1", "CITIZEN");

        HandlerMethodArgumentResolver jwtResolver =
                new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(
                            MethodParameter parameter
                    ) {
                        return parameter.hasParameterAnnotation(
                                AuthenticationPrincipal.class
                        ) && parameter.getParameterType().equals(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return requestJwt;
                    }
                };

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CaseAiSearchController(
                                similarCaseSearchService
                        )
                )
                .setControllerAdvice(new CaseExceptionHandler())
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void searchSimilarCases_success_returnsOk() throws Exception {
        SimilarCaseSearchResponse response =
                new SimilarCaseSearchResponse(
                        "사기 의심 상황입니다.",
                        CaseType.FRAUD,
                        "송금 내역을 보관해 주세요.",
                        List.of(
                                new SimilarCaseResponse(
                                        "SMPA-2026-0001",
                                        "중고 거래 사기",
                                        CaseType.FRAUD,
                                        "송금 후 연락이 끊긴 상황이 유사합니다."
                                )
                        )
                );

        when(similarCaseSearchService.search(
                eq(1),
                any(SimilarCaseSearchRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/cases/ai-search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "situation": "중고 거래 대금을 송금했지만 연락이 끊겼습니다."
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis")
                        .value("사기 의심 상황입니다."))
                .andExpect(jsonPath("$.suggestedCaseType")
                        .value("FRAUD"))
                .andExpect(jsonPath("$.recommendedAction")
                        .value("송금 내역을 보관해 주세요."))
                .andExpect(jsonPath("$.similarCases")
                        .isArray())
                .andExpect(jsonPath("$.similarCases[0].caseId")
                        .value("SMPA-2026-0001"));

        ArgumentCaptor<SimilarCaseSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        SimilarCaseSearchRequest.class
                );

        verify(similarCaseSearchService).search(
                eq(1),
                requestCaptor.capture()
        );

        assertEquals(
                "중고 거래 대금을 송금했지만 연락이 끊겼습니다.",
                requestCaptor.getValue().getSituation()
        );
    }

    @Test
    void searchSimilarCases_blankSituation_returnsBadRequest()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/cases/ai-search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "situation": " "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.message")
                        .value("상황 설명은 필수입니다."));

        verifyNoInteractions(similarCaseSearchService);
    }

    @Test
    void searchSimilarCases_withoutJwt_returnsUnauthorized()
            throws Exception {

        requestJwt = null;

        when(similarCaseSearchService.search(
                isNull(),
                any(SimilarCaseSearchRequest.class)
        )).thenThrow(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인 사용자 정보가 필요합니다."
        ));

        mockMvc.perform(
                        post("/api/v1/cases/ai-search")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "situation": "테스트 상황입니다."
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status")
                        .value(401))
                .andExpect(jsonPath("$.message")
                        .value("로그인 사용자 정보가 필요합니다."));

        verify(similarCaseSearchService).search(
                isNull(),
                any(SimilarCaseSearchRequest.class)
        );
    }

    private Jwt createJwt(String subject, String role) {
        return Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}