package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB 없이 수사기록 등록·조회와 접근 검사 연결 확인
@ExtendWith(MockitoExtension.class)
class InvestigationLogServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 19, 9, 30);

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    // 실제 권한 판단 대신 검사 통과·실패 결과를 설정
    @Mock
    private InvestigationAccessService investigationAccessService;

    @InjectMocks
    private InvestigationLogService investigationLogService;

    private final InvestigationLogCreateRequest request =
            new InvestigationLogCreateRequest(
                    "현장 CCTV 영상 확보",
                    BASE_TIME
            );

    // ==================== 수사기록 등록 테스트 ====================

    @Test
    @DisplayName("사건 담당 경찰을 작성자로 설정하고 수사기록을 저장한다")
    void createInvestigationLog() {
        CaseFile caseFile = savedCase();

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(caseFile);

        // 실제 DB 대신 생성된 기록 번호를 설정해 반환
        when(investigationLogRepository.save(any(InvestigationLog.class)))
                .thenAnswer(invocation -> {
                    InvestigationLog log = invocation.getArgument(0);

                    assertNull(log.getLogId());

                    ReflectionTestUtils.setField(log, "logId", 10);

                    return log;
                });

        InvestigationLogResponse response =
                investigationLogService.createInvestigationLog(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        ArgumentCaptor<InvestigationLog> captor =
                ArgumentCaptor.forClass(InvestigationLog.class);

        verify(investigationLogRepository).save(captor.capture());

        InvestigationLog savedLog = captor.getValue();

        // 접근 검사에서 반환한 사건과 담당 경찰 사용
        assertSame(caseFile, savedLog.getCaseFile());
        assertSame(caseFile.getAssignedOfficer(), savedLog.getOfficer());

        // 입력 내용과 수사 일시 확인
        assertEquals(request.content(), savedLog.getContent());
        assertEquals(request.investigatedAt(), savedLog.getInvestigatedAt());

        // 저장 결과의 응답 DTO 변환 확인
        assertEquals(Integer.valueOf(10), response.logId());
        assertEquals(OFFICER_ID, response.officerId());
        assertEquals(request.content(), response.content());
        assertEquals(request.investigatedAt(), response.investigatedAt());

        // 등록에는 담당 경찰·수사중 상태 검사 사용
        verify(investigationAccessService)
                .findInvestigatingCase(CASE_ID, OFFICER_ID);

        verify(investigationAccessService, never())
                .findReadableCase(anyString(), anyInt());
    }

    @ParameterizedTest(name = "등록 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404, 409})
    @DisplayName("접근 검사에 실패하면 수사기록을 저장하지 않는다")
    void rejectRegistrationWhenAccessFails(int status) {
        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationLogService.createInvestigationLog(
                        CASE_ID,
                        OFFICER_ID,
                        request
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(investigationLogRepository);
    }

    // ==================== 수사기록 목록 조회 테스트 ====================

    @Test
    @DisplayName("조회 권한 확인 후 Repository 순서를 유지하고 DTO로 변환한다")
    void findInvestigationLogs() {
        CaseFile caseFile = savedCase();

        InvestigationLog firstLog = savedLog(
                20,
                caseFile,
                "현장 CCTV 영상 확보",
                BASE_TIME
        );

        InvestigationLog secondLog = savedLog(
                10,
                caseFile,
                "주변 상점 탐문",
                BASE_TIME.plusHours(1)
        );

        // 관리자 또는 담당 경찰의 조회 검사를 통과한 상황
        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenReturn(caseFile);

        // Repository가 수사 일시순으로 반환한 순서 유지
        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of(firstLog, secondLog));

        List<InvestigationLogResponse> responses =
                investigationLogService.findInvestigationLogs(
                        CASE_ID,
                        OFFICER_ID
                );

        // 기록 번호순과 달라도 Repository 반환 순서를 유지
        assertEquals(
                List.of(
                        new InvestigationLogResponse(
                                20,
                                OFFICER_ID,
                                "현장 CCTV 영상 확보",
                                BASE_TIME
                        ),
                        new InvestigationLogResponse(
                                10,
                                OFFICER_ID,
                                "주변 상점 탐문",
                                BASE_TIME.plusHours(1)
                        )
                ),
                responses
        );

        // 조회에는 관리자·담당 경찰용 접근 검사 사용
        verify(investigationAccessService)
                .findReadableCase(CASE_ID, OFFICER_ID);

        verify(investigationAccessService, never())
                .findAssignedCase(anyString(), anyInt());

        verify(investigationAccessService, never())
                .findInvestigatingCase(anyString(), anyInt());

        verify(investigationLogRepository, never())
                .save(any(InvestigationLog.class));
    }

    @Test
    @DisplayName("등록된 수사기록이 없으면 빈 목록을 반환한다")
    void findEmptyInvestigationLogs() {
        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());

        List<InvestigationLogResponse> responses =
                investigationLogService.findInvestigationLogs(
                        CASE_ID,
                        OFFICER_ID
                );

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(investigationAccessService)
                .findReadableCase(CASE_ID, OFFICER_ID);

        verify(investigationLogRepository, never())
                .save(any(InvestigationLog.class));
    }

    @ParameterizedTest(name = "조회 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404})
    @DisplayName("조회 권한 검사에 실패하면 수사기록을 조회하지 않는다")
    void rejectListWhenAccessFails(int status) {
        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationLogService.findInvestigationLogs(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(investigationLogRepository);
    }

    // ==================== 테스트 준비용 메서드 ====================

    // 담당 경찰이 배정된 사건 객체 생성
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

        caseFile.assignOfficer(officer);

        return caseFile;
    }

    // 이미 저장된 수사기록 역할의 테스트 객체
    private InvestigationLog savedLog(
            Integer logId,
            CaseFile caseFile,
            String content,
            LocalDateTime investigatedAt
    ) {
        InvestigationLog log = new InvestigationLog(
                caseFile,
                caseFile.getAssignedOfficer(),
                content,
                investigatedAt
        );

        ReflectionTestUtils.setField(log, "logId", logId);

        return log;
    }
}