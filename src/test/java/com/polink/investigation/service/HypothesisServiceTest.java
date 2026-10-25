package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.HypothesisBasis;
import com.polink.investigation.dto.HypothesisChecklist;
import com.polink.investigation.dto.HypothesisChecklist.CheckItem;
import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.request.HypothesisSaveRequest;
import com.polink.investigation.dto.response.HypothesisResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.Hypothesis;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.*;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB·AI 없이 가설 채택·조회, 분석별 채택 제한, 접근 권한 검사
@ExtendWith(MockitoExtension.class)
class HypothesisServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String OTHER_CASE_ID = "SMPA-2026-0406";
    private static final String EVIDENCE_ID = "EV-2026-0405-01";
    private static final String WITNESS_ID = "W-2026-0405-01";

    private static final Integer OFFICER_ID = 7;
    private static final Integer STATEMENT_ID = 10;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 21, 9, 0);

    @Mock
    private HypothesisRepository hypothesisRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private InvestigationLogRepository investigationLogRepository;

    // 후보 저장소와 JSON 변환은 실제 객체 사용
    private HypothesisCandidateStore candidateStore;
    private ObjectMapper objectMapper;

    private HypothesisService service;
    private User officer;
    private CaseFile caseFile;

    @BeforeEach
    void setUp() {
        candidateStore = new HypothesisCandidateStore();
        objectMapper = JsonMapper.builder().build();

        service = new HypothesisService(
                candidateStore,
                hypothesisRepository,
                caseRepository,
                userRepository,
                evidenceRepository,
                statementRepository,
                witnessRepository,
                investigationLogRepository,
                objectMapper,
                new HypothesisChecklistConverter(objectMapper)
        );

        officer = createUser(OFFICER_ID, UserRole.POLICE);
        caseFile = createCase(CASE_ID);
        caseFile.assignOfficer(officer);

        // 저장 결과 처리 작업의 등록 공간만 준비
        // 실제 DB 트랜잭션을 시작하는 것은 아님
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        // 다음 테스트에 예약 작업이 남지 않도록 정리
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== 가설 채택·체크리스트 저장 ====================

    @Test
    @DisplayName("원본 가설과 미완료 체크리스트를 저장하고 응답에는 원문을 반환한다")
    void adoptHypothesis() {
        prepareAccess();
        prepareValidBasis();
        prepareSaveSuccess();

        HypothesisAiCandidate candidate = candidate(
                "제3자가 현장에 출입했을 가능성이 있다.",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID)
        );

        String candidateId = storeCandidate(candidate);

        HypothesisResponse response = adopt(candidateId);

        ArgumentCaptor<Hypothesis> captor =
                ArgumentCaptor.forClass(Hypothesis.class);

        verify(hypothesisRepository).save(captor.capture());

        Hypothesis saved = captor.getValue();

        // 서버에 보관된 원본 후보의 내용으로 저장됐는지 확인
        assertSame(caseFile, saved.getCaseFile());
        assertSame(officer, saved.getAdoptedBy());
        assertEquals(candidate.content(), saved.getContent());
        assertEquals(candidate.reason(), saved.getReason());
        assertEquals(Integer.valueOf(75), saved.getConfidence());
        assertEquals("AI", saved.getSource());

        // 기존 TEXT 필드에 체크리스트 JSON이 저장되는지 확인
        HypothesisChecklist savedChecklist = objectMapper.readValue(
                saved.getAdditionalRecommendation(),
                HypothesisChecklist.class
        );

        assertEquals(
                candidate.additionalRecommendation(),
                savedChecklist.recommendation()
        );

        // 줄별 확인 항목을 생성하고 최초 상태를 미완료로 설정
        assertEquals(
                List.of(
                        new CheckItem(1, "출입 시각 확인", false),
                        new CheckItem(2, "현장 CCTV 추가 확보", false)
                ),
                savedChecklist.checkItems()
        );

        HypothesisBasis expectedBasis = new HypothesisBasis(
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(), // 수사기록 근거 없음
                candidate.circumstances()
        );

        assertEquals(
                expectedBasis,
                objectMapper.readValue(
                        saved.getBasisJson(),
                        HypothesisBasis.class
                )
        );

        assertEquals(Integer.valueOf(20), response.hypothesisId());
        assertEquals(CASE_ID, response.caseId());
        assertEquals(candidate.content(), response.content());
        assertEquals(candidate.reason(), response.reason());
        assertEquals(Integer.valueOf(75), response.confidence());
        assertEquals("AI", response.source());
        assertEquals(expectedBasis, response.basis());
        assertEquals(OFFICER_ID, response.adoptedByUserId());
        assertEquals(officer.getName(), response.adoptedByName());
        assertEquals(BASE_TIME, response.adoptedAt());

        // 응답에는 JSON 전체가 아닌 추가 확인 사항 원문만 반환
        assertEquals(
                candidate.additionalRecommendation(),
                response.additionalRecommendation()
        );

        // 커밋 전에는 임시 후보 유지
        assertTrue(candidateStore.find(candidateId).isPresent());

        simulateCommit();

        assertTrue(candidateStore.find(candidateId).isEmpty());
    }

    // ==================== 같은 분석에서 하나만 채택 ====================

    @Test
    @DisplayName("하나를 채택 완료하면 같은 분석의 다른 후보도 채택할 수 없다")
    void rejectOtherCandidatesAfterAdoption() {
        prepareAccess();
        prepareSaveSuccess();

        String first = storeCandidate("analysis-1", "가설 A");
        String second = storeCandidate("analysis-1", "가설 B");
        String third = storeCandidate("analysis-1", "가설 C");

        adopt(second);

        // DB 저장이 완료되기 전까지 후보 자체는 보관
        assertTrue(candidateStore.find(first).isPresent());
        assertTrue(candidateStore.find(second).isPresent());
        assertTrue(candidateStore.find(third).isPresent());

        simulateCommit();

        // 같은 분석의 모든 후보를 추가 채택 대상에서 제외
        assertTrue(candidateStore.find(first).isEmpty());
        assertTrue(candidateStore.find(second).isEmpty());
        assertTrue(candidateStore.find(third).isEmpty());

        assertStatus(404, () -> adopt(first));
        assertStatus(404, () -> adopt(second));
        assertStatus(404, () -> adopt(third));

        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("채택 처리 중에는 같은 분석의 다른 후보 요청을 거부한다")
    void rejectOtherCandidateWhileAdopting() {
        prepareAccess();
        prepareSaveSuccess();

        String first = storeCandidate("analysis-1", "가설 A");
        String second = storeCandidate("analysis-1", "가설 B");

        // 아직 커밋 결과를 전달하지 않은 채택 요청
        adopt(first);

        // 첫 요청의 저장 결과가 확정되기 전에는 다른 후보 저장 금지
        assertStatus(409, () -> adopt(second));

        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));

        simulateCommit();

        assertTrue(candidateStore.find(first).isEmpty());
        assertTrue(candidateStore.find(second).isEmpty());
    }

    @Test
    @DisplayName("채택 처리 중 같은 후보를 다시 요청해도 한 번만 저장한다")
    void rejectSameCandidateWhileAdopting() {
        prepareAccess();
        prepareSaveSuccess();

        String candidateId =
                storeCandidate(candidate(List.of(), List.of()));

        adopt(candidateId);

        assertStatus(409, () -> adopt(candidateId));

        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("정상 커밋 후 같은 후보를 다시 채택하면 404를 반환한다")
    void rejectAlreadyAdoptedCandidate() {
        prepareAccess();
        prepareSaveSuccess();

        String candidateId =
                storeCandidate(candidate(List.of(), List.of()));

        adopt(candidateId);
        simulateCommit();

        assertStatus(404, () -> adopt(candidateId));

        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("한 분석의 채택 완료가 다른 분석의 후보에는 영향을 주지 않는다")
    void keepCandidatesFromOtherAnalysis() {
        prepareAccess();
        prepareSaveSuccess();

        String firstAnalysis =
                storeCandidate("analysis-1", "첫 번째 분석의 가설");

        String secondAnalysis =
                storeCandidate("analysis-2", "두 번째 분석의 가설");

        adopt(firstAnalysis);
        simulateCommit();

        assertTrue(candidateStore.find(firstAnalysis).isEmpty());
        assertTrue(candidateStore.find(secondAnalysis).isPresent());

        // 다른 분석에서는 별도로 하나를 채택할 수 있음
        assertDoesNotThrow(() -> adopt(secondAnalysis));

        simulateCommit();

        assertTrue(candidateStore.find(secondAnalysis).isEmpty());

        verify(hypothesisRepository, times(2))
                .save(any(Hypothesis.class));
    }

    // ==================== 저장 실패·취소 결과 처리 ====================

    @Test
    @DisplayName("DB 저장 실패 후 롤백되면 같은 분석의 다른 후보를 다시 채택할 수 있다")
    void allowRetryAfterSaveFailure() {
        prepareAccess();

        String first = storeCandidate("analysis-1", "가설 A");
        String second = storeCandidate("analysis-1", "가설 B");

        RuntimeException saveFailure =
                new IllegalStateException("테스트용 DB 저장 실패");

        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenThrow(saveFailure)
                .thenAnswer(invocation ->
                        assignGeneratedValues(invocation.getArgument(0))
                );

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> adopt(first)
        );

        assertSame(saveFailure, exception);
        assertTrue(candidateStore.find(first).isPresent());
        assertTrue(candidateStore.find(second).isPresent());

        // 현재 구현은 저장 전에 결과 처리 작업을 등록함
        assertEquals(
                1,
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .size()
        );

        simulateCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        // 롤백 결과를 전달하면 채택 처리 상태가 해제됨
        assertDoesNotThrow(() -> adopt(second));

        simulateCommit();

        assertTrue(candidateStore.find(first).isEmpty());
        assertTrue(candidateStore.find(second).isEmpty());

        // 첫 저장 시도 실패 + 두 번째 저장 시도 성공
        verify(hypothesisRepository, times(2))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("롤백 결과가 전달되면 후보를 유지하고 다른 후보의 채택을 허용한다")
    void allowAnotherCandidateAfterRollback() {
        prepareAccess();
        prepareSaveSuccess();

        String first = storeCandidate("analysis-1", "가설 A");
        String second = storeCandidate("analysis-1", "가설 B");

        adopt(first);

        // Repository 호출 후 트랜잭션이 취소된 상황을 전달
        simulateCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        assertTrue(candidateStore.find(first).isPresent());
        assertTrue(candidateStore.find(second).isPresent());

        assertDoesNotThrow(() -> adopt(second));

        simulateCommit();

        assertTrue(candidateStore.find(first).isEmpty());
        assertTrue(candidateStore.find(second).isEmpty());

        verify(hypothesisRepository, times(2))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("저장 결과가 불확실하면 후보를 유지하지만 추가 채택은 제한한다")
    void keepAdoptionBlockedWhenResultUnknown() {
        prepareAccess();
        prepareSaveSuccess();

        String first = storeCandidate("analysis-1", "가설 A");
        String second = storeCandidate("analysis-1", "가설 B");

        adopt(first);

        simulateCompletion(
                TransactionSynchronization.STATUS_UNKNOWN
        );

        assertTrue(candidateStore.find(first).isPresent());
        assertTrue(candidateStore.find(second).isPresent());

        assertStatus(409, () -> adopt(second));

        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("저장 결과 처리 공간이 없으면 채택을 시작하거나 DB에 저장하지 않는다")
    void rejectWithoutTransactionSynchronization() {
        prepareAccess();

        String candidateId =
                storeCandidate(candidate(List.of(), List.of()));

        TransactionSynchronizationManager.clearSynchronization();

        assertThrows(
                IllegalStateException.class,
                () -> adopt(candidateId)
        );

        assertTrue(candidateStore.find(candidateId).isPresent());

        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));

        // 채택 시작 상태를 남기지 않았는지 확인
        assertTrue(candidateStore.tryBeginAdoption(candidateId));
    }

    // ==================== 후보·사건 상태·근거 검증 ====================

    @Test
    @DisplayName("저장소에서 찾을 수 없는 후보는 채택하지 않는다")
    void rejectMissingCandidate() {
        prepareAccess();

        assertAdoptRejected(404, "missing-candidate");

        verifyNoInteractions(
                evidenceRepository,
                statementRepository,
                witnessRepository
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("다른 사건이나 다른 경찰에게 생성된 후보는 채택하지 않는다")
    void rejectCandidateOwnedByOthers(boolean differentCase) {
        prepareAccess();

        String candidateId = candidateStore.put(
                differentCase ? OTHER_CASE_ID : CASE_ID,
                differentCase ? OFFICER_ID : 8,
                candidate(List.of(), List.of()),
                75
        );

        assertAdoptRejected(403, candidateId);
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "INVESTIGATING"
    )
    @DisplayName("수사중이 아닌 사건에서는 가설을 채택하지 않는다")
    void rejectAdoptionWhenNotInvestigating(CaseStatus status) {
        prepareAccess();

        ReflectionTestUtils.setField(caseFile, "status", status);

        assertAdoptRejected(409, "unused-candidate");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("없는 증거물이나 다른 사건의 증거물을 근거로 저장하지 않는다")
    void rejectInvalidEvidence(boolean missingEvidence) {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(EVIDENCE_ID), List.of())
        );

        if (missingEvidence) {
            when(evidenceRepository.findById(EVIDENCE_ID))
                    .thenReturn(Optional.empty());

        } else {
            Evidence evidence = mock(Evidence.class);

            when(evidence.getCaseFile())
                    .thenReturn(createCase(OTHER_CASE_ID));

            when(evidenceRepository.findById(EVIDENCE_ID))
                    .thenReturn(Optional.of(evidence));
        }

        assertAdoptRejected(400, candidateId);

        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("없는 진술을 근거로 가설을 저장하지 않는다")
    void rejectMissingStatement() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of(STATEMENT_ID))
        );

        when(statementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.empty());

        assertAdoptRejected(400, candidateId);
    }

    @Test
    @DisplayName("진술의 목격자가 없으면 가설을 저장하지 않는다")
    void rejectMissingWitness() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of(STATEMENT_ID))
        );

        Statement statement = mock(Statement.class);

        when(statement.getWitnessId()).thenReturn(WITNESS_ID);
        when(statementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(statement));
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.empty());

        assertAdoptRejected(400, candidateId);
    }

    @Test
    @DisplayName("다른 사건 목격자의 진술을 근거로 가설을 저장하지 않는다")
    void rejectStatementFromOtherCase() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of(STATEMENT_ID))
        );

        Statement statement = mock(Statement.class);
        Witness witness = mock(Witness.class);

        when(statement.getWitnessId()).thenReturn(WITNESS_ID);
        when(witness.getCaseFile())
                .thenReturn(createCase(OTHER_CASE_ID));

        when(statementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(statement));
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(witness));

        assertAdoptRejected(400, candidateId);
    }

    // ==================== 기존 가설 조회 ====================

    @Test
    @DisplayName("기존 문자열 가설을 DTO로 변환하고 근거 JSON을 복원한다")
    void findAdoptedHypotheses() {
        prepareAccess();

        HypothesisBasis basis = new HypothesisBasis(
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(), // 수사기록 근거 없음
                List.of("출입 시각 추가 확인 필요")
        );

        Hypothesis newest = savedHypothesis(
                20,
                "최근 채택한 가설",
                objectMapper.writeValueAsString(basis),
                BASE_TIME
        );

        Hypothesis older = savedHypothesis(
                19,
                "이전에 채택한 가설",
                null,
                BASE_TIME.minusHours(1)
        );

        // Repository가 최신순으로 반환하는 상황
        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(newest, older));

        List<HypothesisResponse> responses = findAdopted();

        assertEquals(2, responses.size());

        HypothesisResponse first = responses.get(0);

        assertEquals(Integer.valueOf(20), first.hypothesisId());
        assertEquals(CASE_ID, first.caseId());
        assertEquals("최근 채택한 가설", first.content());
        assertEquals("판단 근거", first.reason());
        assertEquals("추가 확인 사항", first.additionalRecommendation());
        assertEquals(Integer.valueOf(75), first.confidence());
        assertEquals("AI", first.source());
        assertEquals(basis, first.basis());
        assertEquals(OFFICER_ID, first.adoptedByUserId());
        assertEquals(officer.getName(), first.adoptedByName());
        assertEquals(BASE_TIME, first.adoptedAt());

        assertEquals(
                Integer.valueOf(19),
                responses.get(1).hypothesisId()
        );

        // 과거 데이터에 근거 JSON이 없으면 빈 근거 목록 반환
        assertEquals(
                new HypothesisBasis(List.of(), List.of(), List.of(), List.of()),
                responses.get(1).basis()
        );

        verify(hypothesisRepository).findAdoptedByCaseId(CASE_ID);
        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("저장된 가설이 없으면 빈 목록을 반환한다")
    void returnEmptyList() {
        prepareAccess();

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of());

        List<HypothesisResponse> responses = findAdopted();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    @DisplayName("담당 경찰은 사건 상태와 관계없이 저장 가설을 조회한다")
    void allowReadRegardlessOfCaseStatus(CaseStatus status) {
        prepareAccess();

        ReflectionTestUtils.setField(caseFile, "status", status);

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> findAdopted());

        verify(hypothesisRepository).findAdoptedByCaseId(CASE_ID);
    }

    @Test
    @DisplayName("저장된 근거 JSON이 잘못되었으면 예외를 전달한다")
    void rejectInvalidStoredJson() {
        prepareAccess();

        Hypothesis hypothesis = savedHypothesis(
                20,
                "저장 가설",
                "{invalid-json",
                BASE_TIME
        );

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(hypothesis));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> findAdopted()
        );

        assertEquals(
                "저장된 가설의 근거 정보를 읽을 수 없습니다.",
                exception.getMessage()
        );
    }

    // ==================== 체크리스트 JSON 호환 ====================

    @Test
    @DisplayName("체크리스트 JSON에서 원문을 반환하고 기존 체크 상태는 유지한다")
    void findHypothesisWithChecklistJson() {
        prepareAccess();

        HypothesisChecklist storedChecklist =
                new HypothesisChecklist(
                        "CCTV 확인\n목격자 조사",
                        List.of(
                                new CheckItem(1, "CCTV 확인", true),
                                new CheckItem(2, "목격자 조사", false)
                        )
                );

        Hypothesis hypothesis = savedHypothesis(
                20,
                "체크리스트가 있는 가설",
                null,
                BASE_TIME
        );

        hypothesis.updateChecklist(
                objectMapper.writeValueAsString(storedChecklist)
        );

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(hypothesis));

        List<HypothesisResponse> responses = findAdopted();

        assertEquals(1, responses.size());

        // 기존 가설 응답에는 JSON 전체가 아닌 원문만 반환
        assertEquals(
                storedChecklist.recommendation(),
                responses.get(0).additionalRecommendation()
        );

        // 조회 과정에서 기존 완료·미완료 상태가 바뀌지 않는지 확인
        HypothesisChecklist afterRead = objectMapper.readValue(
                hypothesis.getAdditionalRecommendation(),
                HypothesisChecklist.class
        );

        assertEquals(storedChecklist, afterRead);
        assertTrue(afterRead.checkItems().get(0).completed());
        assertFalse(afterRead.checkItems().get(1).completed());

        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("체크리스트 JSON을 읽지 못하면 기존 저장값을 초기화하지 않는다")
    void rejectInvalidStoredChecklistJson() {
        prepareAccess();

        Hypothesis hypothesis = savedHypothesis(
                20,
                "체크리스트가 손상된 가설",
                null,
                BASE_TIME
        );

        String invalidChecklistJson = "{invalid-checklist-json";
        hypothesis.updateChecklist(invalidChecklistJson);

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(hypothesis));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> findAdopted()
        );

        assertEquals(
                "추가 확인 사항을 불러오지 못했습니다.",
                exception.getMessage()
        );

        assertEquals(
                invalidChecklistJson,
                hypothesis.getAdditionalRecommendation()
        );

        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));
    }

    // ==================== 채택·조회 공통 접근 제한 ====================

    @Test
    @DisplayName("로그인 정보가 없으면 채택과 조회를 거부한다")
    void rejectWithoutLogin() {
        assertStatus(401, () ->
                service.adoptHypothesis(
                        CASE_ID,
                        null,
                        new HypothesisSaveRequest("unused")
                )
        );

        assertStatus(401, () ->
                service.findAdoptedHypotheses(CASE_ID, null)
        );

        verifyNoInteractions(
                userRepository,
                caseRepository,
                hypothesisRepository
        );
    }

    @Test
    @DisplayName("로그인 회원을 찾지 못하면 채택과 조회를 거부한다")
    void rejectMissingUser() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.empty());

        assertBothRejected(401);

        verifyNoInteractions(caseRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "POLICE"
    )
    @DisplayName("경찰이 아닌 회원은 채택과 조회를 할 수 없다")
    void rejectNonPolice(UserRole role) {
        User user = createUser(OFFICER_ID, role);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(user));

        assertBothRejected(403);

        verifyNoInteractions(caseRepository);
    }

    @Test
    @DisplayName("없는 사건의 가설은 채택과 조회를 할 수 없다")
    void rejectMissingCase() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        assertBothRejected(404);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("담당 경찰이 없거나 다른 경찰이면 채택과 조회를 거부한다")
    void rejectUnassignedOrOtherOfficer(boolean unassigned) {
        prepareAccess();

        ReflectionTestUtils.setField(
                caseFile,
                "assignedOfficer",
                unassigned ? null : createUser(8, UserRole.POLICE)
        );

        assertBothRejected(403);
    }

    // ==================== 테스트 준비·검증용 메서드 ====================

    // 로그인 회원과 사건이 정상 조회되는 상황 준비
    private void prepareAccess() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
    }

    // 현재 사건에 속한 증거물과 목격자 진술 준비
    private void prepareValidBasis() {
        Evidence evidence = mock(Evidence.class);
        Statement statement = mock(Statement.class);
        Witness witness = mock(Witness.class);

        when(evidence.getCaseFile()).thenReturn(caseFile);
        when(statement.getWitnessId()).thenReturn(WITNESS_ID);
        when(witness.getCaseFile()).thenReturn(caseFile);

        when(evidenceRepository.findById(EVIDENCE_ID))
                .thenReturn(Optional.of(evidence));
        when(statementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(statement));
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(witness));
    }

    // DB가 가설 번호와 채택 일시를 생성하는 상황 재현
    private void prepareSaveSuccess() {
        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenAnswer(invocation ->
                        assignGeneratedValues(invocation.getArgument(0))
                );
    }

    private Hypothesis assignGeneratedValues(Hypothesis hypothesis) {
        ReflectionTestUtils.setField(
                hypothesis,
                "hypothesisId",
                20
        );
        ReflectionTestUtils.setField(
                hypothesis,
                "adoptedAt",
                BASE_TIME
        );

        return hypothesis;
    }

    // 기존 저장 방식으로 후보 하나를 별도 분석에 보관
    private String storeCandidate(HypothesisAiCandidate candidate) {
        return candidateStore.put(
                CASE_ID,
                OFFICER_ID,
                candidate,
                75
        );
    }

    // 동일한 analysisId를 전달하면 같은 분석의 후보로 보관
    private String storeCandidate(
            String analysisId,
            String content
    ) {
        return candidateStore.put(
                analysisId,
                CASE_ID,
                OFFICER_ID,
                candidate(content, List.of(), List.of()),
                75
        );
    }

    private HypothesisAiCandidate candidate(
            List<String> evidenceIds,
            List<Integer> statementIds
    ) {
        return candidate(
                "제3자가 현장에 출입했을 가능성이 있다.",
                evidenceIds,
                statementIds
        );
    }

    // 테스트용 AI 가설 후보 생성
    private HypothesisAiCandidate candidate(
            String content,
            List<String> evidenceIds,
            List<Integer> statementIds
    ) {
        return new HypothesisAiCandidate(
                content,
                "출입 기록과 진술을 함께 검토한 가설",
                "출입 시각 확인\n현장 CCTV 추가 확보",
                evidenceIds,
                statementIds,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("출입 시각 추가 확인 필요")
        );
    }

    // DB에서 조회한 가설 역할의 객체 생성
    private Hypothesis savedHypothesis(
            Integer id,
            String content,
            String basisJson,
            LocalDateTime adoptedAt
    ) {
        Hypothesis hypothesis = new Hypothesis(
                caseFile,
                officer,
                content,
                "판단 근거",
                "추가 확인 사항",
                75,
                basisJson
        );

        ReflectionTestUtils.setField(
                hypothesis,
                "hypothesisId",
                id
        );
        ReflectionTestUtils.setField(
                hypothesis,
                "adoptedAt",
                adoptedAt
        );

        return hypothesis;
    }

    private User createUser(Integer id, UserRole role) {
        User user = new User(
                "test-user-" + id,
                "test-encoded-password",
                "테스트 경찰",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(user, "userId", id);
        ReflectionTestUtils.setField(user, "role", role);

        return user;
    }

    private CaseFile createCase(String caseId) {
        return new CaseFile(
                caseId,
                officer,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                BASE_TIME,
                "테스트 장소"
        );
    }

    private HypothesisResponse adopt(String candidateId) {
        return service.adoptHypothesis(
                CASE_ID,
                OFFICER_ID,
                new HypothesisSaveRequest(candidateId)
        );
    }

    private List<HypothesisResponse> findAdopted() {
        return service.findAdoptedHypotheses(
                CASE_ID,
                OFFICER_ID
        );
    }

    // 실패 상태 코드와 가설 저장 미호출 확인
    private void assertAdoptRejected(
            int status,
            String candidateId
    ) {
        assertStatus(status, () -> adopt(candidateId));

        verify(hypothesisRepository, never())
                .save(any(Hypothesis.class));
    }

    // 동일한 권한 오류가 채택과 조회에 모두 적용되는지 확인
    private void assertBothRejected(int status) {
        assertStatus(status, () -> adopt("unused"));
        assertStatus(status, () -> findAdopted());

        verifyNoInteractions(hypothesisRepository);
    }

    private void assertStatus(
            int expectedStatus,
            Runnable action
    ) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                action::run
        );

        assertEquals(
                expectedStatus,
                exception.getStatusCode().value()
        );
    }

    // 실제 DB 커밋 대신 등록된 정상 커밋·완료 callback 실행
    private void simulateCommit() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();

        try {
            for (TransactionSynchronization synchronization
                    : synchronizations) {
                synchronization.afterCommit();
            }

            for (TransactionSynchronization synchronization
                    : synchronizations) {
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                );
            }

        } finally {
            resetSynchronizations();
        }
    }

    // 실제 DB 처리 없이 롤백 또는 결과 불명 상태를 전달
    private void simulateCompletion(int status) {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();

        try {
            for (TransactionSynchronization synchronization
                    : synchronizations) {
                synchronization.afterCompletion(status);
            }

        } finally {
            resetSynchronizations();
        }
    }

    // 다음 요청에 이전 요청의 callback이 남지 않도록 정리
    private void resetSynchronizations() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
    }
}