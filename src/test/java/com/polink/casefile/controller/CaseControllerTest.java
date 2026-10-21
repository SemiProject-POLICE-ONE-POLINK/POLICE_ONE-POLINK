package com.polink.casefile.controller;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.request.CaseOfficerRequest;
import com.polink.casefile.dto.response.*;
import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.exception.CaseExceptionHandler;
import com.polink.casefile.service.CaseService;
import com.polink.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class CaseControllerTest {

    @Mock
    private CaseService caseService;

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
                .standaloneSetup(new CaseController(caseService))
                .setControllerAdvice(new CaseExceptionHandler())
                .setCustomArgumentResolvers(
                        jwtResolver,
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();
    }

    @Test
    void closeCase_blankReason_returnsBadRequest() throws Exception {
        requestJwt = createJwt("11", "ADMIN");

        mockMvc.perform(
                        post("/api/v1/cases/{caseId}/close", "SMPA-2026-0042")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"closeReason\":\"  \"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("사건 종결 사유는 필수입니다."));

        verifyNoInteractions(caseService);
    }

    @Test
    void closeCase_success_returnsOk() throws Exception {
        requestJwt = createJwt("11", "ADMIN");

        CaseFile caseFile = mock(CaseFile.class);
        when(caseFile.getStatus()).thenReturn(CaseStatus.CLOSED);

        CaseClose caseClose = mock(CaseClose.class);
        when(caseClose.getCaseId()).thenReturn("SMPA-2026-0042");
        when(caseClose.getCaseFile()).thenReturn(caseFile);
        when(caseClose.getClosedAt())
                .thenReturn(LocalDateTime.of(2026, 10, 20, 15, 0));

        CaseCloseResponse response = new CaseCloseResponse(caseClose);
        when(caseService.closeCase(
                eq(11),
                eq("SMPA-2026-0042"),
                any(CaseCloseRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/cases/{caseId}/close", "SMPA-2026-0042")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"closeReason\":\"사건 처리 완료\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("SMPA-2026-0042"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        ArgumentCaptor<CaseCloseRequest> requestCaptor =
                ArgumentCaptor.forClass(CaseCloseRequest.class);

        verify(caseService).closeCase(
                eq(11),
                eq("SMPA-2026-0042"),
                requestCaptor.capture()
        );
        assertEquals(
                "사건 처리 완료",
                requestCaptor.getValue().getCloseReason()
        );
    }

    @Test
    void closeCase_withoutJwt_returnsUnauthorized() throws Exception {
        requestJwt = null;

        when(caseService.closeCase(
                isNull(),
                eq("SMPA-2026-0042"),
                any(CaseCloseRequest.class)
        )).thenThrow(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인 사용자 정보가 필요합니다."
        ));

        mockMvc.perform(
                        post("/api/v1/cases/{caseId}/close", "SMPA-2026-0042")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"closeReason\":\"사건 처리 완료\"}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("로그인 사용자 정보가 필요합니다."));

        verify(caseService).closeCase(
                isNull(),
                eq("SMPA-2026-0042"),
                any(CaseCloseRequest.class)
        );
    }

    @Test
    void createCase_success_returnsCreated() throws Exception {
        CaseCreateResponse response =
                new CaseCreateResponse(
                        "SMPA-2026-0043",
                        CaseStatus.WAITING
                );

        when(caseService.createCase(
                eq(1),
                any(CaseCreateRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/cases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "휴대전화 절도",
                                          "caseType": "THEFT",
                                          "description": "지하철에서 휴대전화를 도난당했습니다.",
                                          "occurredAt": "2026-10-21T10:30:00",
                                          "location": "서울역"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId")
                        .value("SMPA-2026-0043"))
                .andExpect(jsonPath("$.status")
                        .value("WAITING"));

        verify(caseService).createCase(
                eq(1),
                any(CaseCreateRequest.class)
        );
    }

    @Test
    void createCase_blankTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/cases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": " ",
                                          "caseType": "THEFT",
                                          "description": "사건 내용",
                                          "occurredAt": "2026-10-21T10:30:00",
                                          "location": "서울역"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("사건명은 필수입니다."));

        verifyNoInteractions(caseService);
    }

    @Test
    void getCases_success_returnsOk() throws Exception {
        CaseListResponse response =
                new CaseListResponse(
                        new PageImpl<CaseFile>(List.of())
                );

        when(caseService.getCases(
                eq(1),
                any(Pageable.class)
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/cases")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(caseService).getCases(
                eq(1),
                pageableCaptor.capture()
        );

        assertEquals(
                0,
                pageableCaptor.getValue().getPageNumber()
        );
        assertEquals(
                10,
                pageableCaptor.getValue().getPageSize()
        );
    }

    @Test
    void getMyCases_success_returnsOk() throws Exception {
        MyCaseListResponse response =
                new MyCaseListResponse(
                        new PageImpl<CaseFile>(List.of())
                );

        when(caseService.getMyCases(
                eq(1),
                any(Pageable.class)
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/cases/my")
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(caseService).getMyCases(
                eq(1),
                any(Pageable.class)
        );

        verify(caseService, never())
                .getCaseDetail(any(), anyString());
    }

    @Test
    void getCaseDetail_success_returnsOk() throws Exception {
        CaseFile caseFile = mock(CaseFile.class);

        when(caseFile.getCaseId())
                .thenReturn("SMPA-2026-0042");
        when(caseFile.getTitle())
                .thenReturn("휴대전화 절도");
        when(caseFile.getCaseType())
                .thenReturn(CaseType.THEFT);
        when(caseFile.getDescription())
                .thenReturn("지하철에서 휴대전화를 도난당했습니다.");
        when(caseFile.getOccurredAt())
                .thenReturn(
                        LocalDateTime.of(2026, 10, 21, 10, 30)
                );
        when(caseFile.getLocation())
                .thenReturn("서울역");
        when(caseFile.getStatus())
                .thenReturn(CaseStatus.WAITING);
        when(caseFile.getAssignedOfficer())
                .thenReturn(null);

        CaseDetailResponse response =
                new CaseDetailResponse(caseFile);

        when(caseService.getCaseDetail(
                1,
                "SMPA-2026-0042"
        )).thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/v1/cases/{caseId}",
                                "SMPA-2026-0042"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId")
                        .value("SMPA-2026-0042"))
                .andExpect(jsonPath("$.title")
                        .value("휴대전화 절도"))
                .andExpect(jsonPath("$.caseType")
                        .value("THEFT"))
                .andExpect(jsonPath("$.description")
                        .value("지하철에서 휴대전화를 도난당했습니다."))
                .andExpect(jsonPath("$.location")
                        .value("서울역"))
                .andExpect(jsonPath("$.status")
                        .value("WAITING"));

        verify(caseService).getCaseDetail(
                1,
                "SMPA-2026-0042"
        );
    }

    @Test
    void assignOfficer_success_returnsOk() throws Exception {
        requestJwt = createJwt("11", "ADMIN");

        User officer = mock(User.class);
        when(officer.getUserId())
                .thenReturn(7);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseFile.getCaseId())
                .thenReturn("SMPA-2026-0042");
        when(caseFile.getAssignedOfficer())
                .thenReturn(officer);
        when(caseFile.getStatus())
                .thenReturn(CaseStatus.INVESTIGATING);

        CaseOfficerResponse response =
                new CaseOfficerResponse(caseFile);

        when(caseService.assignOfficer(
                eq(11),
                eq("SMPA-2026-0042"),
                any(CaseOfficerRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        patch(
                                "/api/v1/cases/{caseId}/officer",
                                "SMPA-2026-0042"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "officerId": 7
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId")
                        .value("SMPA-2026-0042"))
                .andExpect(jsonPath("$.assignedOfficerId")
                        .value(7))
                .andExpect(jsonPath("$.status")
                        .value("INVESTIGATING"));

        ArgumentCaptor<CaseOfficerRequest> requestCaptor =
                ArgumentCaptor.forClass(CaseOfficerRequest.class);

        verify(caseService).assignOfficer(
                eq(11),
                eq("SMPA-2026-0042"),
                requestCaptor.capture()
        );

        assertEquals(
                7,
                requestCaptor.getValue().getOfficerId()
        );
    }

    @Test
    void assignOfficer_invalidOfficerId_returnsBadRequest()
            throws Exception {

        requestJwt = createJwt("11", "ADMIN");

        mockMvc.perform(
                        patch(
                                "/api/v1/cases/{caseId}/officer",
                                "SMPA-2026-0042"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "officerId": 0
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.message")
                        .value("회원 번호는 양수여야 합니다."));

        verifyNoInteractions(caseService);
    }

    private Jwt createJwt(String subject, String role) {
        return Jwt.withTokenValue("test-access-token")
                .header("alg", "none")
                .subject(subject)
                .claim("role", role)
                .build();
    }
}
