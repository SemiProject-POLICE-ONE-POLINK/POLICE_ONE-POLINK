package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.response.InvestigationBoardResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.EvidenceRepository;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestigationBoardServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 19, 10, 0);

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InvestigationBoardService investigationBoardService;

    @Test
    @DisplayName("경찰관이 조회하면 사건 자료를 요약하고 각 목록을 최대 4개 반환한다")
    void findInvestigationBoard() {
        User officer = savedUser(OFFICER_ID, UserRole.POLICE);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase(officer)));

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-05"),
                        savedEvidence("EV-2026-0405-02"),
                        savedEvidence("EV-2026-0405-01"),
                        savedEvidence("EV-2026-0405-04"),
                        savedEvidence("EV-2026-0405-03")
                ));

        Witness witness1 = savedWitness(
                "W-2026-0405-01",
                "박서연",
                BASE_TIME.plusMinutes(1)
        );
        Witness witness2 = savedWitness(
                "W-2026-0405-02",
                "이석훈",
                BASE_TIME.plusMinutes(2)
        );
        Witness witness3 = savedWitness(
                "W-2026-0405-03",
                "정하늘",
                BASE_TIME.plusMinutes(3)
        );
        Witness witness4 = savedWitness(
                "W-2026-0405-04",
                "김민재",
                BASE_TIME.plusMinutes(4)
        );
        Witness witness5 = savedWitness(
                "W-2026-0405-05",
                "최경수",
                BASE_TIME.plusMinutes(5)
        );

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        witness5,
                        witness3,
                        witness1,
                        witness4,
                        witness2
                ));

        List<String> displayedWitnessIds = List.of(
                witness1.getWitnessId(),
                witness2.getWitnessId(),
                witness3.getWitnessId(),
                witness4.getWitnessId()
        );

        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        displayedWitnessIds
                ))
                .thenReturn(List.of(
                        savedStatement(
                                1,
                                witness1.getWitnessId(),
                                "처음에는 검은색 차량으로 보였습니다.",
                                StatementType.INITIAL,
                                BASE_TIME.plusMinutes(10)
                        ),
                        savedStatement(
                                2,
                                witness2.getWitnessId(),
                                "오토바이가 지나가는 것을 봤습니다.",
                                StatementType.INITIAL,
                                BASE_TIME.plusMinutes(11)
                        ),
                        savedStatement(
                                3,
                                witness3.getWitnessId(),
                                "사람의 얼굴은 보지 못했습니다.",
                                StatementType.INITIAL,
                                BASE_TIME.plusMinutes(12)
                        ),
                        savedStatement(
                                4,
                                witness1.getWitnessId(),
                                "차량 번호 끝자리가 7이었습니다.",
                                StatementType.ADDITIONAL,
                                BASE_TIME.plusMinutes(13)
                        )
                ));

        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of(
                        savedLog(1, officer, "현장 확인", BASE_TIME.plusHours(1)),
                        savedLog(2, officer, "CCTV 확보", BASE_TIME.plusHours(2)),
                        savedLog(3, officer, "목격자 조사", BASE_TIME.plusHours(3)),
                        savedLog(4, officer, "차량 조회", BASE_TIME.plusHours(4)),
                        savedLog(5, officer, "추가 탐문", BASE_TIME.plusHours(5))
                ));

        InvestigationBoardResponse response =
                investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        OFFICER_ID
                );

        assertEquals(CASE_ID, response.caseInfo().caseId());
        assertEquals("편의점 절도 사건", response.caseInfo().title());
        assertEquals("THEFT", response.caseInfo().caseType());
        assertEquals("INVESTIGATING", response.caseInfo().status());
        assertEquals(
                OFFICER_ID,
                response.caseInfo().assignedOfficerId()
        );

        assertEquals(5, response.totalEvidenceCount());
        assertEquals(4, response.evidences().size());
        assertEquals(
                "EV-2026-0405-01",
                response.evidences().get(0).evidenceId()
        );
        assertEquals(
                "EV-2026-0405-04",
                response.evidences().get(3).evidenceId()
        );

        assertEquals(5, response.totalWitnessCount());
        assertEquals(4, response.witnesses().size());
        assertEquals(
                witness1.getWitnessId(),
                response.witnesses().get(0).witnessId()
        );
        assertEquals(2, response.witnesses().get(0).statementCount());
        assertEquals(
                "차량 번호 끝자리가 7이었습니다.",
                response.witnesses().get(0).latestStatementContent()
        );
        assertEquals(
                StatementType.ADDITIONAL,
                response.witnesses().get(0).latestStatementType()
        );
        assertEquals(0, response.witnesses().get(3).statementCount());
        assertNull(
                response.witnesses().get(3).latestStatementContent()
        );
        assertNull(
                response.witnesses().get(3).latestStatementType()
        );

        assertEquals(5, response.totalInvestigationLogCount());
        assertEquals(4, response.investigationLogs().size());
        assertEquals(
                Integer.valueOf(5),
                response.investigationLogs().get(0).logId()
        );
        assertEquals(
                Integer.valueOf(2),
                response.investigationLogs().get(3).logId()
        );

        verify(evidenceRepository, never()).save(any());
        verify(witnessRepository, never()).save(any());
        verify(statementRepository, never()).save(any());
        verify(investigationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("수사 자료가 없으면 전체 개수 0과 빈 목록을 반환한다")
    void findEmptyInvestigationBoard() {
        User officer = savedUser(OFFICER_ID, UserRole.POLICE);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase(officer)));
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());

        InvestigationBoardResponse response =
                investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        OFFICER_ID
                );

        assertEquals(0, response.totalEvidenceCount());
        assertNotNull(response.evidences());
        assertTrue(response.evidences().isEmpty());

        assertEquals(0, response.totalWitnessCount());
        assertNotNull(response.witnesses());
        assertTrue(response.witnesses().isEmpty());

        assertEquals(0, response.totalInvestigationLogCount());
        assertNotNull(response.investigationLogs());
        assertTrue(response.investigationLogs().isEmpty());

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("로그인 정보가 없으면 401 예외가 발생한다")
    void rejectWithoutLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        null
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(
                userRepository,
                caseRepository,
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 401 예외가 발생한다")
    void rejectUnknownUser() {
        when(userRepository.findById(99999))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        99999
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(
                caseRepository,
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @ParameterizedTest(name = "{0} 사용자의 수사보드 접근 거부")
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "ADMIN"}
    )
    @DisplayName("경찰관이 아니면 403 예외가 발생한다")
    void rejectNonPolice(UserRole role) {
        Integer userId = 11;

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(savedUser(userId, role)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        userId
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(
                caseRepository,
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @Test
    @DisplayName("사건이 없으면 404 예외가 발생하고 수사 자료를 조회하지 않는다")
    void rejectMissingCase() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(
                        savedUser(OFFICER_ID, UserRole.POLICE)
                ));
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @Test
    @DisplayName("다른 경찰관이 담당한 사건이면 403 예외가 발생한다")
    void rejectOfficerNotAssigned() {
        Integer otherOfficerId = 8;

        when(userRepository.findById(otherOfficerId))
                .thenReturn(Optional.of(
                        savedUser(otherOfficerId, UserRole.POLICE)
                ));
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(
                        savedCase(savedUser(OFFICER_ID, UserRole.POLICE))
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        otherOfficerId
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @Test
    @DisplayName("담당 경찰관이 배정되지 않은 사건이면 403 예외가 발생한다")
    void rejectUnassignedCase() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(
                        savedUser(OFFICER_ID, UserRole.POLICE)
                ));
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase(null)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationBoardService.findInvestigationBoard(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    private CaseFile savedCase(User assignedOfficer) {
        CaseFile caseFile = new CaseFile(
                CASE_ID,
                savedUser(1, UserRole.CITIZEN),
                "편의점 절도 사건",
                CaseType.THEFT,
                "편의점에서 물품이 도난당한 사건",
                BASE_TIME.minusDays(1),
                "서울시 강남구"
        );

        if (assignedOfficer != null) {
            caseFile.assignOfficer(assignedOfficer);
        }

        return caseFile;
    }

    private User savedUser(Integer userId, UserRole role) {
        User user = new User(
                "user" + userId,
                "encoded-password",
                "테스트 회원",
                "010-0000-0000"
        );
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private Evidence savedEvidence(String evidenceId) {
        return new Evidence(
                evidenceId,
                savedCase(null),
                "CCTV 영상",
                EvidenceType.VIDEO,
                "현장 주변 CCTV 영상",
                BASE_TIME,
                "사건 현장"
        );
    }

    private Witness savedWitness(
            String witnessId,
            String name,
            LocalDateTime createdAt
    ) {
        return new Witness(
                witnessId,
                savedCase(null),
                name,
                "010-1234-5678",
                "목격자",
                createdAt
        );
    }

    private Statement savedStatement(
            Integer statementId,
            String witnessId,
            String content,
            StatementType statementType,
            LocalDateTime createdAt
    ) {
        Statement statement = new Statement(
                witnessId,
                content,
                statementType,
                createdAt
        );
        ReflectionTestUtils.setField(
                statement,
                "statementId",
                statementId
        );
        return statement;
    }

    private InvestigationLog savedLog(
            Integer logId,
            User officer,
            String content,
            LocalDateTime investigatedAt
    ) {
        InvestigationLog log = new InvestigationLog(
                savedCase(null),
                officer,
                content,
                investigatedAt
        );
        ReflectionTestUtils.setField(log, "logId", logId);
        return log;
    }
}