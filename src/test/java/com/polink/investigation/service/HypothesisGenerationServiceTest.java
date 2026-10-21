package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import com.polink.investigation.dto.response.HypothesisGenerateResponse;
import com.polink.investigation.entity.Evidence;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 실제 DB·OpenAI 호출 없이 입력 구성, 권한, 후보 생성 흐름을 검사
@ExtendWith(MockitoExtension.class)
class HypothesisGenerationServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String EVIDENCE_ID = "EV-2026-0405-01";
    private static final String WITNESS_ID = "W-2026-0405-01";
    private static final Integer OFFICER_ID = 7;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    @Mock
    private HypothesisAiClient hypothesisAiClient;

    @Mock
    private HypothesisResponseValidator hypothesisResponseValidator;

    @Mock
    private HypothesisConfidenceCalculator confidenceCalculator;

    @Mock
    private HypothesisCandidateStore candidateStore;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private HypothesisGenerationService service;

    @BeforeEach
    void setUp() {
        service = new HypothesisGenerationService(
                hypothesisAiClient,
                candidateStore,
                transactionManager,
                hypothesisResponseValidator,
                confidenceCalculator,
                caseRepository,
                userRepository,
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }

    @Test
    @DisplayName("증거물·진술·수사기록 전체를 AI 입력으로 구성한다")
    void buildAiInputWithAllMaterials() {
        stubAccessibleCase();

        // 수사보드 요약 개수인 4개로 잘리지 않는지 확인
        List<Evidence> evidences = new ArrayList<>();
        for (int number = 1; number <= 5; number++) {
            Evidence evidence = mock(Evidence.class);
            when(evidence.getEvidenceId())
                    .thenReturn("EV-2026-0405-0" + number);
            when(evidence.getEvidenceName())
                    .thenReturn("증거물 " + number);
            when(evidence.getDescription())
                    .thenReturn("증거물 설명 " + number);
            evidences.add(evidence);
        }
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(evidences);

        Witness witness = mock(Witness.class);
        when(witness.getWitnessId()).thenReturn(WITNESS_ID);
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(witness));

        Statement statement = mock(Statement.class);
        when(statement.getStatementId()).thenReturn(7);
        when(statement.getWitnessId()).thenReturn(WITNESS_ID);
        when(statement.getStatementType())
                .thenReturn(StatementType.INITIAL);
        when(statement.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 10, 21, 10, 0));
        when(statement.getContent())
                .thenReturn("현장에서 차량을 봤습니다.");

        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(WITNESS_ID)
                ))
                .thenReturn(List.of(statement));

        InvestigationLog log = mock(InvestigationLog.class);
        when(log.getLogId()).thenReturn(1);
        when(log.getContent()).thenReturn("CCTV를 확인했습니다.");
        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of(log));

        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        assertEquals(CASE_ID, input.caseId());
        assertEquals(5, input.evidences().size());
        assertEquals(1, input.statements().size());
        assertEquals(1, input.investigationLogs().size());
        assertEquals(7, input.statements().get(0).statementId());
    }

    @Test
    @DisplayName("로그인 정보가 없으면 가설 생성을 거부한다")
    void rejectMissingLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(hypothesisAiClient);
    }

    @Test
    @DisplayName("경찰이 아니면 가설 생성을 거부한다")
    void rejectNonPolice() {
        User citizen = mock(User.class);
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(citizen));
        when(citizen.getRole()).thenReturn(UserRole.CITIZEN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(hypothesisAiClient);
    }

    @Test
    @DisplayName("담당 경찰이 배정되지 않은 사건은 가설 생성을 거부한다")
    void rejectUnassignedCase() {
        User officer = mock(User.class);
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        // getAssignedOfficer()의 기본 반환값 null → 미배정 사건

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(hypothesisAiClient);
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {"WAITING", "CLOSED", "CANCELED"}
    )
    @DisplayName("수사중이 아닌 사건은 가설 생성을 거부한다")
    void rejectNonInvestigatingCase(CaseStatus caseStatus) {
        User officer = mock(User.class);
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);
        when(officer.getUserId()).thenReturn(OFFICER_ID);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        when(caseFile.getAssignedOfficer()).thenReturn(officer);
        when(caseFile.getStatus()).thenReturn(caseStatus);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(hypothesisAiClient);
    }

    @Test
    @DisplayName("분석할 수사 자료가 없으면 가설 생성을 거부한다")
    void rejectNoMaterials() {
        User officer = mock(User.class);
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);
        when(officer.getUserId()).thenReturn(OFFICER_ID);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        when(caseFile.getAssignedOfficer()).thenReturn(officer);
        when(caseFile.getStatus()).thenReturn(CaseStatus.INVESTIGATING);

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(hypothesisAiClient);
    }

    @Test
    @DisplayName("AI 원본 후보를 검증하고 점수·임시 번호를 붙여 반환한다")
    void generateHypotheses() {
        stubAccessibleCase();
        stubOneEvidence();
        stubReadOnlyTransaction();

        HypothesisAiCandidate rawCandidate = rawCandidate();

        // AI 모의 객체는 화면 응답이 아닌 원본 후보를 반환한다.
        when(hypothesisAiClient.generate(any(HypothesisAiInput.class)))
                .thenReturn(List.of(rawCandidate));

        // 서버 계산기와 임시 저장소의 결과를 모의 설정한다.
        when(confidenceCalculator.calculate(rawCandidate))
                .thenReturn(35);
        when(candidateStore.put(
                eq(CASE_ID),
                eq(OFFICER_ID),
                eq(rawCandidate),
                eq(35)
        )).thenReturn("candidate-1");

        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        assertEquals(CASE_ID, response.caseId());
        assertEquals(1, response.hypotheses().size());

        HypothesisCandidateResponse displayed =
                response.hypotheses().get(0);

        assertEquals("candidate-1", displayed.candidateId());
        assertEquals(rawCandidate.content(), displayed.content());
        assertEquals(35, displayed.confidence());
        assertEquals(rawCandidate.evidenceIds(), displayed.evidenceIds());

        verify(hypothesisResponseValidator).validate(
                any(HypothesisAiInput.class),
                eq(List.of(rawCandidate))
        );
        verify(confidenceCalculator, times(1))
                .calculate(rawCandidate);
        verify(candidateStore).put(
                CASE_ID,
                OFFICER_ID,
                rawCandidate,
                35
        );
    }

    @Test
    @DisplayName("AI 응답 검증에 실패하면 후보를 보관하지 않는다")
    void rejectInvalidAiResponse() {
        stubAccessibleCase();
        stubOneEvidence();
        stubReadOnlyTransaction();

        when(hypothesisAiClient.generate(any(HypothesisAiInput.class)))
                .thenReturn(List.of());

        ResponseStatusException validationException =
                new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 분석 결과를 확인할 수 없습니다."
                );

        doThrow(validationException)
                .when(hypothesisResponseValidator)
                .validate(any(HypothesisAiInput.class), eq(List.of()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        assertSame(validationException, exception);
        verifyNoInteractions(confidenceCalculator, candidateStore);
    }

    // 정상 권한과 수사중 상태를 가진 사건 준비
    private void stubAccessibleCase() {
        User officer = mock(User.class);
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);
        when(officer.getUserId()).thenReturn(OFFICER_ID);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        when(caseFile.getAssignedOfficer()).thenReturn(officer);
        when(caseFile.getStatus()).thenReturn(CaseStatus.INVESTIGATING);
        when(caseFile.getCaseId()).thenReturn(CASE_ID);
        when(caseFile.getTitle()).thenReturn("테스트 사건");
        when(caseFile.getDescription()).thenReturn("테스트 사건 설명");
    }

    // 생성 테스트에 사용할 증거물 한 건 준비
    private void stubOneEvidence() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(EVIDENCE_ID);
        when(evidence.getEvidenceName()).thenReturn("현장 사진");
        when(evidence.getDescription()).thenReturn("현장 사진 설명");

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(evidence));
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());
    }

    // 읽기 전용 TransactionTemplate이 사용할 모의 트랜잭션
    private void stubReadOnlyTransaction() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(transactionStatus);
    }

    // 점수는 포함하지 않는 AI 원본 후보
    private HypothesisAiCandidate rawCandidate() {
        return new HypothesisAiCandidate(
                "테스트 가설",
                "현장 사진을 근거로 작성했습니다.",
                "주변 자료를 추가 확인해 주세요.",
                List.of(EVIDENCE_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}