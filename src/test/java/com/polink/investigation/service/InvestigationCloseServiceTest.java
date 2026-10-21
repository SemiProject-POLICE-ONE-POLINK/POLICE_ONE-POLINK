package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.investigation.dto.request.InvestigationCloseRequest;
import com.polink.investigation.dto.response.InvestigationCloseResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB 없이 수사 종결 처리와 접근 검사 연결 확인
@ExtendWith(MockitoExtension.class)
class InvestigationCloseServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 19, 9, 30);

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    @Mock
    private InvestigationAccessService investigationAccessService;

    @InjectMocks
    private InvestigationCloseService investigationCloseService;

    private final InvestigationCloseRequest request =
            new InvestigationCloseRequest("피의자 검거 및 증거 확보 완료");

    @Test
    @DisplayName("담당 경찰이 수사를 종결하면 종결 사유를 수사기록으로 저장하고 응답한다")
    void closeInvestigation() {

        // 준비: 접근 검사를 통과한 수사중 사건
        CaseFile caseFile = savedCase();

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(caseFile);

        // 실제 DB 대신 생성된 기록 번호를 설정해 반환
        when(investigationLogRepository.save(any(InvestigationLog.class)))
                .thenAnswer(invocation -> {
                    InvestigationLog log = invocation.getArgument(0);
                    assertNull(log.getLogId());
                    ReflectionTestUtils.setField(log, "logId", 20);
                    return log;
                });

        LocalDateTime beforeRequest = LocalDateTime.now();

        // 실행
        InvestigationCloseResponse response =
                investigationCloseService.closeInvestigation(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        // 저장 요청에 전달된 종결 수사기록 확인
        ArgumentCaptor<InvestigationLog> captor =
                ArgumentCaptor.forClass(InvestigationLog.class);
        verify(investigationLogRepository).save(captor.capture());

        InvestigationLog savedLog = captor.getValue();

        // 확인: 접근 검사의 사건·담당 경찰로 "수사 종결: 사유" 저장
        assertSame(caseFile, savedLog.getCaseFile());
        assertSame(caseFile.getAssignedOfficer(), savedLog.getOfficer());
        assertEquals("수사 종결: " + request.closeReason(), savedLog.getContent());

        // 확인: 종결 일시는 서버의 현재 시각
        assertNotNull(savedLog.getInvestigatedAt());
        assertFalse(savedLog.getInvestigatedAt().isBefore(beforeRequest));

        // 확인: 응답에 사건·기록·사유 반영
        assertEquals(CASE_ID, response.caseId());
        assertEquals(Integer.valueOf(20), response.logId());
        assertEquals(OFFICER_ID, response.officerId());
        assertEquals(request.closeReason(), response.closeReason());
        assertEquals(savedLog.getInvestigatedAt(), response.closedAt());

        // TODO: A의 finishInvestigation() 반영 후 주석 해제
        // assertEquals("INVESTIGATION_DONE", response.status());
        // assertEquals(CaseStatus.INVESTIGATION_DONE, caseFile.getStatus());

        verify(investigationAccessService)
                .findInvestigatingCase(CASE_ID, OFFICER_ID);
    }

    @ParameterizedTest(name = "접근 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404, 409})
    @DisplayName("접근 검사에 실패하면 수사 종결 기록을 저장하지 않는다")
    void rejectCloseWhenAccessFails(int status) {

        // 준비: 로그인 없음·권한 없음·사건 없음·수사중 아님 상황
        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        // 실행
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationCloseService.closeInvestigation(
                        CASE_ID,
                        OFFICER_ID,
                        request
                )
        );

        // 확인: 접근 검사의 예외를 그대로 전달하고 저장하지 않음
        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());
        verifyNoInteractions(investigationLogRepository);
    }

    // 담당 경찰이 배정되어 수사중인 사건 역할의 테스트 객체
    private CaseFile savedCase() {

        User officer = new User(
                "test-police",
                "test-password-hash",
                "테스트 경찰관",
                "010-1234-5678"
        );

        ReflectionTestUtils.setField(officer, "userId", OFFICER_ID);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        CaseFile caseFile = new CaseFile(
                CASE_ID,
                officer,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                BASE_TIME,
                "테스트 장소"
        );

        // 배정하면 사건 상태가 INVESTIGATING으로 변경됨
        caseFile.assignOfficer(officer);

        return caseFile;
    }
}