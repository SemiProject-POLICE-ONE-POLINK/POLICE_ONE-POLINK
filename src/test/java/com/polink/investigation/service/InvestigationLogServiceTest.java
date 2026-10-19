package com.polink.investigation.service;

import com.polink.investigation.dto.request.InvestigationLogCreateRequest;
import com.polink.investigation.dto.response.InvestigationLogResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.CaseExistenceRepository;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// 실제 DB에 연결하지 않고 수사기록 등록·조회 Service의 처리 로직 검사
@ExtendWith(MockitoExtension.class)
class InvestigationLogServiceTest {

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    @Mock
    private CaseExistenceRepository caseExistenceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InvestigationLogService investigationLogService;

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 1;
    private static final LocalDateTime INVESTIGATED_AT =
            LocalDateTime.of(2026, 10, 19, 9, 30);

    private final InvestigationLogCreateRequest request =
            new InvestigationLogCreateRequest("현장 CCTV 영상 확보", INVESTIGATED_AT);

    // ============================
    // 수사기록 등록
    // ============================
    @Nested
    @DisplayName("수사기록 등록")
    class Create {

        @Test
        @DisplayName("경찰관이 존재하는 사건에 등록하면 요청 내용으로 저장하고 응답한다")
        void createInvestigationLog() {

            User officer = savedUser(OFFICER_ID, UserRole.POLICE);

            when(userRepository.findById(OFFICER_ID))
                    .thenReturn(Optional.of(officer));
            when(caseExistenceRepository.existsByCaseId(CASE_ID))
                    .thenReturn(true);

            // DB가 새 기록 번호를 생성하는 상황을 재현
            when(investigationLogRepository.save(any(InvestigationLog.class)))
                    .thenAnswer(invocation -> {
                        InvestigationLog log = invocation.getArgument(0);
                        assertNull(log.getLogId()); // 새 기록으로 저장 요청하는지 확인
                        ReflectionTestUtils.setField(log, "logId", 10);
                        return log;
                    });

            // 실행
            InvestigationLogResponse response =
                    investigationLogService.createInvestigationLog(
                            CASE_ID, OFFICER_ID, request
                    );

            // 저장 요청에 전달된 수사기록 확인
            ArgumentCaptor<InvestigationLog> captor =
                    ArgumentCaptor.forClass(InvestigationLog.class);
            verify(investigationLogRepository).save(captor.capture());

            InvestigationLog saved = captor.getValue();
            assertEquals(CASE_ID, saved.getCaseId());
            assertSame(officer, saved.getOfficer());
            assertEquals(request.content(), saved.getContent());
            assertEquals(request.investigatedAt(), saved.getInvestigatedAt());

            // 저장 결과가 응답 DTO에 반영되는지 확인
            assertEquals(Integer.valueOf(10), response.logId());
            assertEquals(OFFICER_ID, response.officerId());
            assertEquals(request.content(), response.content());
            assertEquals(request.investigatedAt(), response.investigatedAt());

            verifyNoMoreInteractions(investigationLogRepository);
        }

        @Test
        @DisplayName("로그인 정보가 없으면 401 예외가 발생하고 아무것도 조회·저장하지 않는다")
        void rejectWithoutLogin() {

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.createInvestigationLog(
                            CASE_ID, null, request
                    )
            );

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            verifyNoInteractions(
                    userRepository,
                    caseExistenceRepository,
                    investigationLogRepository
            );
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 401 예외가 발생하고 저장하지 않는다")
        void rejectUnknownUser() {

            when(userRepository.findById(9999))
                    .thenReturn(Optional.empty());

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.createInvestigationLog(
                            CASE_ID, 9999, request
                    )
            );

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            verifyNoInteractions(caseExistenceRepository, investigationLogRepository);
        }

        // 등록은 경찰관만 가능 — 시민·관리자는 거부
        @ParameterizedTest(name = "{0} 회원 등록 거부")
        @EnumSource(value = UserRole.class, names = {"CITIZEN", "ADMIN"})
        @DisplayName("경찰관이 아니면 403 예외가 발생하고 저장하지 않는다")
        void rejectNonPolice(UserRole role) {

            when(userRepository.findById(2))
                    .thenReturn(Optional.of(savedUser(2, role)));

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.createInvestigationLog(
                            CASE_ID, 2, request
                    )
            );

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            verifyNoInteractions(caseExistenceRepository, investigationLogRepository);
        }

        @Test
        @DisplayName("사건이 없으면 404 예외가 발생하고 저장하지 않는다")
        void rejectMissingCase() {

            when(userRepository.findById(OFFICER_ID))
                    .thenReturn(Optional.of(savedUser(OFFICER_ID, UserRole.POLICE)));
            when(caseExistenceRepository.existsByCaseId(CASE_ID))
                    .thenReturn(false);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.createInvestigationLog(
                            CASE_ID, OFFICER_ID, request
                    )
            );

            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            verifyNoInteractions(investigationLogRepository);
        }
    }

    // ============================
    // 수사기록 목록 조회
    // ============================
    @Nested
    @DisplayName("수사기록 목록 조회")
    class Find {

        // 조회는 경찰관·관리자 모두 가능
        @ParameterizedTest(name = "{0} 회원 조회")
        @EnumSource(value = UserRole.class, names = {"POLICE", "ADMIN"})
        @DisplayName("Repository가 반환한 순서(수사 일시 오름차순) 그대로 응답한다")
        void findInvestigationLogs(UserRole role) {

            User viewer = savedUser(5, role);
            User officer = savedUser(OFFICER_ID, UserRole.POLICE);

            // Repository는 수사 일시·기록 번호 오름차순으로 반환한다고 가정
            List<InvestigationLog> logs = List.of(
                    savedLog(1, officer, "탐문 수사", INVESTIGATED_AT.minusHours(1)),
                    savedLog(2, officer, "CCTV 영상 확보", INVESTIGATED_AT)
            );

            when(userRepository.findById(5)).thenReturn(Optional.of(viewer));
            when(caseExistenceRepository.existsByCaseId(CASE_ID)).thenReturn(true);
            when(investigationLogRepository
                    .findByCaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                    .thenReturn(logs);

            List<InvestigationLogResponse> responses =
                    investigationLogService.findInvestigationLogs(CASE_ID, 5);

            assertEquals(2, responses.size());

            InvestigationLogResponse first = responses.get(0);
            assertEquals(Integer.valueOf(1), first.logId());
            assertEquals(OFFICER_ID, first.officerId());
            assertEquals("탐문 수사", first.content());
            assertEquals(INVESTIGATED_AT.minusHours(1), first.investigatedAt());

            assertEquals(Integer.valueOf(2), responses.get(1).logId());

            // 조회 요청으로 데이터를 저장·변경하지 않는지 확인
            verify(investigationLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("수사기록이 없는 사건이면 빈 목록을 응답한다")
        void findEmpty() {

            when(userRepository.findById(OFFICER_ID))
                    .thenReturn(Optional.of(savedUser(OFFICER_ID, UserRole.POLICE)));
            when(caseExistenceRepository.existsByCaseId(CASE_ID)).thenReturn(true);
            when(investigationLogRepository
                    .findByCaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                    .thenReturn(List.of());

            List<InvestigationLogResponse> responses =
                    investigationLogService.findInvestigationLogs(CASE_ID, OFFICER_ID);

            assertTrue(responses.isEmpty());
        }

        @Test
        @DisplayName("로그인 정보가 없으면 401 예외가 발생하고 아무것도 조회하지 않는다")
        void rejectWithoutLogin() {

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.findInvestigationLogs(CASE_ID, null)
            );

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            verifyNoInteractions(
                    userRepository,
                    caseExistenceRepository,
                    investigationLogRepository
            );
        }

        @Test
        @DisplayName("존재하지 않는 회원이면 401 예외가 발생한다")
        void rejectUnknownUser() {

            when(userRepository.findById(9999)).thenReturn(Optional.empty());

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.findInvestigationLogs(CASE_ID, 9999)
            );

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            verifyNoInteractions(caseExistenceRepository, investigationLogRepository);
        }

        @Test
        @DisplayName("시민 회원이면 403 예외가 발생하고 기록을 조회하지 않는다")
        void rejectCitizen() {

            when(userRepository.findById(2))
                    .thenReturn(Optional.of(savedUser(2, UserRole.CITIZEN)));

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.findInvestigationLogs(CASE_ID, 2)
            );

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
            verifyNoInteractions(caseExistenceRepository, investigationLogRepository);
        }

        @Test
        @DisplayName("사건이 없으면 404 예외가 발생하고 기록을 조회하지 않는다")
        void rejectMissingCase() {

            when(userRepository.findById(OFFICER_ID))
                    .thenReturn(Optional.of(savedUser(OFFICER_ID, UserRole.POLICE)));
            when(caseExistenceRepository.existsByCaseId(anyString())).thenReturn(false);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> investigationLogService.findInvestigationLogs(
                            "SMPA-2026-9999", OFFICER_ID
                    )
            );

            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            verifyNoInteractions(investigationLogRepository);
        }
    }

    // DB에서 조회한 회원 역할의 테스트 객체 생성
    // User에 id·role setter가 없으므로 ReflectionTestUtils로 설정
    private User savedUser(Integer userId, UserRole role) {
        User user = new User("user" + userId, "encoded-pw", "테스트회원", "010-0000-0000");
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    // DB에서 조회한 수사기록 역할의 테스트 객체 생성
    private InvestigationLog savedLog(
            Integer logId, User officer, String content, LocalDateTime investigatedAt
    ) {
        InvestigationLog log = new InvestigationLog(CASE_ID, officer, content, investigatedAt);
        ReflectionTestUtils.setField(log, "logId", logId);
        return log;
    }
}