package com.polink.casefile.controller;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.response.CaseCloseResponse;
import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.exception.CaseExceptionHandler;
import com.polink.casefile.service.CaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class CaseControllerTest {

    @Mock
    private CaseService caseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CaseController(caseService))
                .setControllerAdvice(new CaseExceptionHandler())
                .build();
    }

    @Test
    void closeCase_blankReason_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/cases/{caseId}/close", "SMPA-2026-0042")
                                .header("X-User-Id", 11)
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
                                .header("X-User-Id", 11)
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
    void closeCase_withoutHeader_returnsUnauthorized() throws Exception {
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
}
