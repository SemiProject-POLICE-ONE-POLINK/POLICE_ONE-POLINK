package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import com.polink.investigation.dto.response.HypothesisGenerateResponse;
import com.polink.investigation.entity.*;
import com.polink.investigation.repository.*;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 실제 DB·OpenAI 호출 없이 입력 구성, 권한, 중복 제외, 후보 보관 흐름 검사
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
    private HypothesisRepository hypothesisRepository;

    @Mock
    private HypothesisHistoryStore historyStore;

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
                hypothesisRepository,
                historyStore,
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

    // ==================== AI 입력 자료 구성 ====================

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

        LocalDateTime createdAt =
                LocalDateTime.of(2026, 10, 21, 10, 0);

        Statement statement = mock(Statement.class);
        when(statement.getStatementId()).thenReturn(7);
        when(statement.getWitnessId()).thenReturn(WITNESS_ID);
        when(statement.getStatementType())
                .thenReturn(StatementType.INITIAL);
        when(statement.getCreatedAt()).thenReturn(createdAt);
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
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(
                        CASE_ID
                ))
                .thenReturn(List.of(log));

        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        assertEquals(CASE_ID, input.caseId());
        assertEquals("테스트 사건", input.caseTitle());
        assertEquals("테스트 사건 설명", input.caseDescription());

        assertEquals(5, input.evidences().size());
        assertEquals(
                "EV-2026-0405-05",
                input.evidences().get(4).evidenceId()
        );

        assertEquals(1, input.statements().size());
        assertEquals(7, input.statements().get(0).statementId());
        assertEquals(
                WITNESS_ID,
                input.statements().get(0).witnessId()
        );
        assertEquals(
                StatementType.INITIAL,
                input.statements().get(0).statementType()
        );
        assertEquals(
                createdAt,
                input.statements().get(0).createdAt()
        );

        assertEquals(1, input.investigationLogs().size());
        assertEquals(
                "CCTV를 확인했습니다.",
                input.investigationLogs().get(0).content()
        );
    }

    @Test
    @DisplayName("목격자가 없으면 진술을 조회하지 않고 빈 목록을 전달한다")
    void skipStatementLookupWithoutWitnesses() {
        stubAccessibleCase();
        stubOneEvidence();

        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        assertTrue(input.statements().isEmpty());
        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("이전 생성 가설과 채택된 가설을 중복 없이 AI 입력에 포함한다")
    void includePreviousAndAdoptedHypotheses() {
        stubAccessibleCase();
        stubOneEvidence();

        when(historyStore.getHistory(CASE_ID, OFFICER_ID))
                .thenReturn(List.of("이전 가설 A", "공통 가설"));

        Hypothesis duplicated = mock(Hypothesis.class);
        when(duplicated.getContent()).thenReturn(" 공통 가설 ");

        Hypothesis adopted = mock(Hypothesis.class);
        when(adopted.getContent()).thenReturn("채택된 가설 B");

        Hypothesis blank = mock(Hypothesis.class);
        when(blank.getContent()).thenReturn(" ");

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(duplicated, adopted, blank));

        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        assertEquals(
                List.of("이전 가설 A", "공통 가설", "채택된 가설 B"),
                input.previousHypotheses()
        );
    }

    // ==================== 로그인·권한·사건 상태 ====================

    @Test
    @DisplayName("로그인 정보가 없으면 가설 생성을 거부한다")
    void rejectMissingLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, null)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
        verifyNoInteractions(userRepository, hypothesisAiClient);
    }

    @Test
    @DisplayName("로그인한 회원을 찾을 수 없으면 가설 생성을 거부한다")
    void rejectMissingUser() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
        verifyNoInteractions(caseRepository, hypothesisAiClient);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "ADMIN"}
    )
    @DisplayName("시민과 관리자는 수사 가설을 생성할 수 없다")
    void rejectNonPolice(UserRole role) {
        User user = mock(User.class);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(role);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
        verifyNoInteractions(caseRepository, hypothesisAiClient);
    }

    @Test
    @DisplayName("존재하지 않는 사건은 가설 생성을 거부한다")
    void rejectMissingCase() {
        User officer = mock(User.class);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
        verifyNoInteractions(evidenceRepository, hypothesisAiClient);
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

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
        verifyNoInteractions(evidenceRepository, hypothesisAiClient);
    }

    @Test
    @DisplayName("다른 경찰이 담당하는 사건은 가설 생성을 거부한다")
    void rejectOtherOfficerCase() {
        User loggedInOfficer = mock(User.class);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(loggedInOfficer));
        when(loggedInOfficer.getRole()).thenReturn(UserRole.POLICE);
        when(loggedInOfficer.getUserId()).thenReturn(OFFICER_ID);

        User assignedOfficer = mock(User.class);
        when(assignedOfficer.getUserId()).thenReturn(8);

        CaseFile caseFile = mock(CaseFile.class);
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        when(caseFile.getAssignedOfficer())
                .thenReturn(assignedOfficer);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
        verifyNoInteractions(evidenceRepository, hypothesisAiClient);
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = "INVESTIGATING",
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("수사중이 아닌 사건은 가설 생성을 거부한다")
    void rejectNonInvestigatingCase(CaseStatus status) {
        stubAssignedCase(status);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        verifyNoInteractions(evidenceRepository, hypothesisAiClient);
    }

    @Test
    @DisplayName("증거물·진술·수사기록이 모두 없으면 가설 생성을 거부한다")
    void rejectNoMaterials() {
        stubAssignedCase(CaseStatus.INVESTIGATING);

        // Repository 모의 객체의 목록 조회 기본 반환값은 빈 목록
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
        verifyNoInteractions(hypothesisAiClient, candidateStore);
    }

    // ==================== 가설 생성·분석 번호·생성 이력 ====================

    @Test
    @DisplayName("검증된 후보에 서버 점수와 임시 번호를 붙이고 생성 이력을 보관한다")
    void generateHypotheses() {
        HypothesisAiCandidate candidate =
                rawCandidate("새로운 가설");

        stubGeneration(List.of(candidate));
        stubSuccessfulCandidateStorage();

        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        assertEquals(CASE_ID, response.caseId());
        assertEquals(1, response.hypotheses().size());

        HypothesisCandidateResponse displayed =
                response.hypotheses().get(0);

        assertNotNull(displayed.candidateId());
        assertDoesNotThrow(
                () -> UUID.fromString(displayed.candidateId())
        );
        assertEquals(candidate.content(), displayed.content());
        assertEquals(candidate.reason(), displayed.reason());
        assertEquals(35, displayed.confidence());
        assertEquals(candidate.evidenceIds(), displayed.evidenceIds());

        verify(hypothesisResponseValidator).validate(
                any(HypothesisAiInput.class),
                eq(List.of(candidate))
        );

        verify(historyStore).remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate)
        );

        // 생성 단계에서는 채택 가설을 DB에 저장하지 않음
        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));

        // 사건 자료 조회 트랜잭션을 끝낸 후 외부 AI 호출
        InOrder order = inOrder(
                transactionManager,
                hypothesisAiClient
        );

        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(transactionStatus);
        order.verify(hypothesisAiClient)
                .generate(any(HypothesisAiInput.class));
    }

    @Test
    @DisplayName("후보 3개에 같은 분석 번호를 부여하고 서로 다른 후보 번호를 반환한다")
    void storeThreeCandidatesUnderSameAnalysis() {
        List<HypothesisAiCandidate> candidates = List.of(
                rawCandidate("가설 A"),
                rawCandidate("가설 B"),
                rawCandidate("가설 C")
        );

        stubGeneration(candidates);
        stubSuccessfulCandidateStorage();

        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        ArgumentCaptor<String> analysisIds =
                ArgumentCaptor.forClass(String.class);

        verify(candidateStore, times(3)).put(
                analysisIds.capture(),
                eq(CASE_ID),
                eq(OFFICER_ID),
                any(HypothesisAiCandidate.class),
                eq(35)
        );

        List<String> captured = analysisIds.getAllValues();

        assertEquals(captured.get(0), captured.get(1));
        assertEquals(captured.get(0), captured.get(2));
        assertDoesNotThrow(
                () -> UUID.fromString(captured.get(0))
        );

        assertEquals(3, response.hypotheses().size());
        assertEquals(
                3L,
                response.hypotheses().stream()
                        .map(HypothesisCandidateResponse::candidateId)
                        .distinct()
                        .count()
        );

        verify(historyStore).remember(
                CASE_ID,
                OFFICER_ID,
                candidates
        );
    }

    // ==================== 중복 후보 제외 ====================

    @Test
    @DisplayName("이전 생성 가설과 채택된 가설을 제외하고 새로운 후보만 반환한다")
    void excludePreviousAndAdoptedCandidates() {
        HypothesisAiCandidate previous =
                rawCandidate("  PREVIOUS   hypothesis  ");
        HypothesisAiCandidate adopted =
                rawCandidate("채택된 가설");
        HypothesisAiCandidate fresh =
                rawCandidate("새로운 가설");

        stubGeneration(List.of(previous, adopted, fresh));
        stubSuccessfulCandidateStorage();

        when(historyStore.getHistory(CASE_ID, OFFICER_ID))
                .thenReturn(List.of("previous hypothesis"));

        Hypothesis saved = mock(Hypothesis.class);
        when(saved.getContent()).thenReturn("채택된 가설");

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(saved));

        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        assertEquals(1, response.hypotheses().size());
        assertEquals(
                fresh.content(),
                response.hypotheses().get(0).content()
        );

        verify(confidenceCalculator, never()).calculate(previous);
        verify(confidenceCalculator, never()).calculate(adopted);

        verify(candidateStore, times(1)).put(
                anyString(),
                eq(CASE_ID),
                eq(OFFICER_ID),
                eq(fresh),
                eq(35)
        );

        verify(historyStore).remember(
                CASE_ID,
                OFFICER_ID,
                List.of(fresh)
        );

        // 이전 가설 목록이 실제 AI 호출 입력에도 포함되는지 확인
        ArgumentCaptor<HypothesisAiInput> inputCaptor =
                ArgumentCaptor.forClass(HypothesisAiInput.class);

        verify(hypothesisAiClient).generate(inputCaptor.capture());

        assertEquals(
                List.of("previous hypothesis", "채택된 가설"),
                inputCaptor.getValue().previousHypotheses()
        );
    }

    @Test
    @DisplayName("이번 AI 응답 안에서 공백과 대소문자만 다른 후보를 한 번만 반환한다")
    void excludeDuplicatesWithinSameResponse() {
        HypothesisAiCandidate first =
                rawCandidate("Vehicle hypothesis");
        HypothesisAiCandidate duplicate =
                rawCandidate("  VEHICLE   hypothesis  ");
        HypothesisAiCandidate other =
                rawCandidate("다른 가설");

        stubGeneration(List.of(first, duplicate, other));
        stubSuccessfulCandidateStorage();

        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        assertEquals(
                List.of(first.content(), other.content()),
                response.hypotheses().stream()
                        .map(HypothesisCandidateResponse::content)
                        .toList()
        );

        verify(confidenceCalculator, never()).calculate(duplicate);

        verify(candidateStore, times(2)).put(
                anyString(),
                eq(CASE_ID),
                eq(OFFICER_ID),
                any(HypothesisAiCandidate.class),
                eq(35)
        );

        verify(historyStore).remember(
                CASE_ID,
                OFFICER_ID,
                List.of(first, other)
        );
    }

    @Test
    @DisplayName("모든 후보가 이전 가설과 중복되면 409를 반환하고 후보를 보관하지 않는다")
    void rejectWhenAllCandidatesAreDuplicates() {
        HypothesisAiCandidate candidate =
                rawCandidate("기존 가설");

        stubGeneration(List.of(candidate));

        when(historyStore.getHistory(CASE_ID, OFFICER_ID))
                .thenReturn(List.of("기존 가설"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(confidenceCalculator, candidateStore);

        verify(historyStore, never()).remember(
                anyString(),
                anyInt(),
                anyList()
        );

        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));
    }

    // ==================== 외부 AI·응답 검증 실패 ====================

    @Test
    @DisplayName("AI 응답 검증에 실패하면 후보와 생성 이력을 보관하지 않는다")
    void rejectInvalidAiResponse() {
        stubGeneration(List.of());

        ResponseStatusException validationException =
                new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 분석 결과를 확인할 수 없습니다."
                );

        doThrow(validationException)
                .when(hypothesisResponseValidator)
                .validate(
                        any(HypothesisAiInput.class),
                        eq(List.of())
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        assertSame(validationException, exception);
        verifyNoInteractions(confidenceCalculator, candidateStore);

        verify(historyStore, never()).remember(
                anyString(),
                anyInt(),
                anyList()
        );
    }

    @Test
    @DisplayName("외부 AI 호출에 실패하면 후보와 생성 이력을 보관하지 않는다")
    void rejectAiCallFailure() {
        stubAccessibleCase();
        stubOneEvidence();
        stubReadOnlyTransaction();

        ResponseStatusException aiException =
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI 추론 중 문제가 발생했습니다."
                );

        when(hypothesisAiClient.generate(any(HypothesisAiInput.class)))
                .thenThrow(aiException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        assertSame(aiException, exception);

        verifyNoInteractions(
                hypothesisResponseValidator,
                confidenceCalculator,
                candidateStore
        );

        verify(historyStore, never()).remember(
                anyString(),
                anyInt(),
                anyList()
        );
    }

    // ==================== 공통 테스트 자료 ====================

    // 담당 경찰과 사건 상태를 준비하고 사건 모의 객체 반환
    private CaseFile stubAssignedCase(CaseStatus status) {
        User officer = mock(User.class);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(officer.getRole()).thenReturn(UserRole.POLICE);
        when(officer.getUserId()).thenReturn(OFFICER_ID);

        CaseFile caseFile = mock(CaseFile.class);

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
        when(caseFile.getAssignedOfficer()).thenReturn(officer);
        when(caseFile.getStatus()).thenReturn(status);

        return caseFile;
    }

    // 정상 권한과 수사중 상태를 가진 사건의 기본 정보 준비
    private void stubAccessibleCase() {
        CaseFile caseFile =
                stubAssignedCase(CaseStatus.INVESTIGATING);

        when(caseFile.getCaseId()).thenReturn(CASE_ID);
        when(caseFile.getTitle()).thenReturn("테스트 사건");
        when(caseFile.getDescription())
                .thenReturn("테스트 사건 설명");
    }

    // 생성 테스트에 사용할 증거물 한 건 준비
    private void stubOneEvidence() {
        Evidence evidence = mock(Evidence.class);

        when(evidence.getEvidenceId()).thenReturn(EVIDENCE_ID);
        when(evidence.getEvidenceName()).thenReturn("현장 사진");
        when(evidence.getDescription())
                .thenReturn("현장 사진 설명");

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(evidence));

        // 목격자·수사기록은 모의 객체의 기본 반환값인 빈 목록 사용
    }

    // 읽기 전용 TransactionTemplate이 사용할 모의 트랜잭션
    private void stubReadOnlyTransaction() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(transactionStatus);
    }

    // 정상적인 사건 자료와 AI 원본 응답 준비
    private void stubGeneration(
            List<HypothesisAiCandidate> candidates
    ) {
        stubAccessibleCase();
        stubOneEvidence();
        stubReadOnlyTransaction();

        when(hypothesisAiClient.generate(any(HypothesisAiInput.class)))
                .thenReturn(candidates);
    }

    // 서버 계산 점수와 후보별로 다른 임시 번호를 반환하도록 설정
    private void stubSuccessfulCandidateStorage() {
        when(confidenceCalculator.calculate(
                any(HypothesisAiCandidate.class)
        )).thenReturn(35);

        when(candidateStore.put(
                anyString(),
                eq(CASE_ID),
                eq(OFFICER_ID),
                any(HypothesisAiCandidate.class),
                eq(35)
        )).thenAnswer(invocation -> UUID.randomUUID().toString());
    }

    // 점수는 포함하지 않는 AI 원본 후보
    private HypothesisAiCandidate rawCandidate(String content) {
        return new HypothesisAiCandidate(
                content,
                "현장 사진을 근거로 작성했습니다.",
                "주변 자료 추가 확인",
                List.of(EVIDENCE_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}