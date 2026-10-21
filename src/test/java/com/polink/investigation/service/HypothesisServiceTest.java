package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.HypothesisBasis;
import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.request.HypothesisSaveRequest;
import com.polink.investigation.dto.response.HypothesisResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.Hypothesis;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.EvidenceRepository;
import com.polink.investigation.repository.HypothesisRepository;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
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

// 실제 DB·AI 없이 가설 채택·조회와 접근 제한을 검사
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

    // 후보 저장소와 JSON 변환은 실제 객체로 검사
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
                objectMapper
        );

        officer = createUser(OFFICER_ID, UserRole.POLICE);
        caseFile = createCase(CASE_ID);
        caseFile.assignOfficer(officer);

        // Service를 직접 호출하므로 커밋 후 실행할 작업의 보관소만 준비
        // 실제 DB 트랜잭션이 시작되는 것은 아님
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        // 다른 테스트에 커밋·롤백 예약 작업이 남지 않도록 정리
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ==================== 가설 채택 테스트 ====================

    @Test
    @DisplayName("서버에 보관된 후보 내용과 점수를 저장하고 응답한다")
    void adoptHypothesis() {
        prepareAccess();

        HypothesisAiCandidate candidate = candidate(
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID)
        );

        String candidateId = storeCandidate(candidate);
        prepareValidBasis();

        // DB가 번호와 채택 일시를 생성한 상황을 대신 설정
        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenAnswer(invocation -> {
                    Hypothesis hypothesis = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            hypothesis, "hypothesisId", 20
                    );
                    ReflectionTestUtils.setField(
                            hypothesis, "adoptedAt", BASE_TIME
                    );
                    return hypothesis;
                });

        HypothesisResponse response = service.adoptHypothesis(
                CASE_ID,
                OFFICER_ID,
                new HypothesisSaveRequest(candidateId)
        );

        // 저장 요청에 실제로 전달된 가설 확인
        ArgumentCaptor<Hypothesis> captor =
                ArgumentCaptor.forClass(Hypothesis.class);

        verify(hypothesisRepository).save(captor.capture());

        Hypothesis saved = captor.getValue();

        assertSame(caseFile, saved.getCaseFile());
        assertSame(officer, saved.getAdoptedBy());
        assertEquals(candidate.content(), saved.getContent());
        assertEquals(candidate.reason(), saved.getReason());
        assertEquals(
                candidate.additionalRecommendation(),
                saved.getAdditionalRecommendation()
        );
        assertEquals(Integer.valueOf(75), saved.getConfidence());
        assertEquals("AI", saved.getSource());

        HypothesisBasis expectedBasis = new HypothesisBasis(
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                candidate.circumstances()
        );

        // 근거 번호와 정황이 JSON으로 저장되는지 확인
        assertEquals(
                expectedBasis,
                objectMapper.readValue(
                        saved.getBasisJson(),
                        HypothesisBasis.class
                )
        );

        // 저장 결과가 응답 DTO에 반영되는지 확인
        assertEquals(Integer.valueOf(20), response.hypothesisId());
        assertEquals(CASE_ID, response.caseId());
        assertEquals(candidate.content(), response.content());
        assertEquals(candidate.reason(), response.reason());
        assertEquals(
                candidate.additionalRecommendation(),
                response.additionalRecommendation()
        );
        assertEquals(Integer.valueOf(75), response.confidence());
        assertEquals("AI", response.source());
        assertEquals(expectedBasis, response.basis());
        assertEquals(OFFICER_ID, response.adoptedByUserId());
        assertEquals(officer.getName(), response.adoptedByName());
        assertEquals(BASE_TIME, response.adoptedAt());

        // 커밋 전에는 후보 유지
        assertTrue(candidateStore.find(candidateId).isPresent());

        // 정상 커밋 상황을 전달하면 후보 제거
        simulateCommit();

        assertTrue(candidateStore.find(candidateId).isEmpty());
    }

    @Test
    @DisplayName("정상 커밋 후 같은 후보로 다시 채택하면 저장하지 않는다")
    void rejectAlreadyAdoptedCandidate() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of())
        );

        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HypothesisSaveRequest request =
                new HypothesisSaveRequest(candidateId);

        service.adoptHypothesis(CASE_ID, OFFICER_ID, request);
        simulateCommit();

        assertStatus(404, () ->
                service.adoptHypothesis(CASE_ID, OFFICER_ID, request)
        );

        // 첫 요청만 저장되고 두 번째 요청은 저장되지 않음
        verify(hypothesisRepository, times(1))
                .save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("DB 저장에 실패하면 예외를 전달하고 후보를 유지한다")
    void keepCandidateWhenSaveFails() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of())
        );

        RuntimeException saveFailure =
                new IllegalStateException("테스트용 DB 저장 실패");

        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenThrow(saveFailure);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.adoptHypothesis(
                        CASE_ID,
                        OFFICER_ID,
                        new HypothesisSaveRequest(candidateId)
                )
        );

        assertSame(saveFailure, exception);
        assertTrue(candidateStore.find(candidateId).isPresent());

        // 저장 실패 이후에는 후보 제거 작업도 예약되지 않음
        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("롤백 결과가 전달되면 임시 후보를 제거하지 않는다")
    void keepCandidateWhenRolledBack() {
        prepareAccess();

        String candidateId = storeCandidate(
                candidate(List.of(), List.of())
        );

        when(hypothesisRepository.save(any(Hypothesis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.adoptHypothesis(
                CASE_ID,
                OFFICER_ID,
                new HypothesisSaveRequest(candidateId)
        );

        // afterCommit은 호출하지 않고 롤백 완료 상황만 전달
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {

            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK
            );
        }

        assertTrue(candidateStore.find(candidateId).isPresent());
    }

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

    // ==================== 가설 조회 테스트 ====================

    @Test
    @DisplayName("저장 가설을 DTO로 변환하고 근거 JSON을 복원한다")
    void findAdoptedHypotheses() {
        prepareAccess();

        HypothesisBasis basis = new HypothesisBasis(
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
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

        // Repository가 최신순으로 반환한 결과를 준비
        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of(newest, older));

        List<HypothesisResponse> responses =
                service.findAdoptedHypotheses(CASE_ID, OFFICER_ID);

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

        // 과거 데이터에 근거 JSON이 없으면 빈 목록으로 응답
        assertEquals(
                new HypothesisBasis(List.of(), List.of(), List.of()),
                responses.get(1).basis()
        );

        verify(hypothesisRepository).findAdoptedByCaseId(CASE_ID);
        verify(hypothesisRepository, never()).save(any(Hypothesis.class));
    }

    @Test
    @DisplayName("저장된 가설이 없으면 빈 목록을 반환한다")
    void returnEmptyList() {
        prepareAccess();

        when(hypothesisRepository.findAdoptedByCaseId(CASE_ID))
                .thenReturn(List.of());

        List<HypothesisResponse> responses =
                service.findAdoptedHypotheses(CASE_ID, OFFICER_ID);

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

        assertDoesNotThrow(() ->
                service.findAdoptedHypotheses(CASE_ID, OFFICER_ID)
        );

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
                () -> service.findAdoptedHypotheses(
                        CASE_ID, OFFICER_ID
                )
        );

        assertEquals(
                "저장된 가설의 근거 정보를 읽을 수 없습니다.",
                exception.getMessage()
        );
    }

    // ==================== 채택·조회 공통 접근 제한 ====================

    @Test
    @DisplayName("로그인 정보가 없으면 채택과 조회를 거부한다")
    void rejectWithoutLogin() {
        assertStatus(401, () ->
                service.adoptHypothesis(
                        CASE_ID, null,
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

    // ==================== 테스트 준비·검증 도우미 ====================

    // 로그인 회원과 사건 조회가 정상 처리되는 상황 준비
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

    // 가설 채택 요청에 사용할 서버 보관 후보 생성
    private String storeCandidate(HypothesisAiCandidate candidate) {
        return candidateStore.put(
                CASE_ID,
                OFFICER_ID,
                candidate,
                75
        );
    }

    private HypothesisAiCandidate candidate(
            List<String> evidenceIds,
            List<Integer> statementIds
    ) {
        return new HypothesisAiCandidate(
                "제3자가 현장에 출입했을 가능성",
                "출입 기록과 진술을 함께 검토한 가설",
                "출입 시각을 추가 확인해주세요.",
                evidenceIds,
                statementIds,
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

        ReflectionTestUtils.setField(hypothesis, "hypothesisId", id);
        ReflectionTestUtils.setField(hypothesis, "adoptedAt", adoptedAt);

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

    // 실패 상태 코드와 저장 미호출을 함께 확인
    private void assertAdoptRejected(int status, String candidateId) {
        assertStatus(status, () ->
                service.adoptHypothesis(
                        CASE_ID,
                        OFFICER_ID,
                        new HypothesisSaveRequest(candidateId)
                )
        );

        verify(hypothesisRepository, never()).save(any(Hypothesis.class));
    }

    // 동일한 권한 오류가 채택과 조회에 모두 적용되는지 확인
    private void assertBothRejected(int status) {
        assertStatus(status, () ->
                service.adoptHypothesis(
                        CASE_ID,
                        OFFICER_ID,
                        new HypothesisSaveRequest("unused")
                )
        );

        assertStatus(status, () ->
                service.findAdoptedHypotheses(CASE_ID, OFFICER_ID)
        );

        verifyNoInteractions(hypothesisRepository);
    }

    private void assertStatus(int expectedStatus, Runnable action) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                action::run
        );

        assertEquals(
                expectedStatus,
                exception.getStatusCode().value()
        );
    }

    // 실제 DB 커밋 대신 예약된 정상 커밋 작업을 직접 실행
    private void simulateCommit() {
        for (TransactionSynchronization synchronization :
                TransactionSynchronizationManager.getSynchronizations()) {

            synchronization.afterCommit();
            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED
            );
        }
    }
}