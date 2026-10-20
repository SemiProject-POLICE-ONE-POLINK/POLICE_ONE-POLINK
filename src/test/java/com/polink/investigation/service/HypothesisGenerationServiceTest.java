package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import com.polink.investigation.dto.response.HypothesisGenerateResponse;
import com.polink.investigation.entity.*;
import com.polink.investigation.repository.*;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 DB·AI 호출 없이 입력 구성·접근 조건·생성 흐름 검사
@ExtendWith(MockitoExtension.class)
class HypothesisGenerationServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String WITNESS_ID = "W-2026-0405-01";
    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 21, 10, 0);

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

    @InjectMocks
    private HypothesisGenerationService service;

    // ==================== AI 입력 자료 구성 테스트 ====================

    @Test
    @DisplayName("전체 수사 자료를 AI 입력 DTO로 구성한다")
    void buildAiInput() {

        CaseFile caseFile = prepareAccessibleCase();

        // 수사보드처럼 4개로 잘리지 않는지 확인하기 위해 5개 준비
        List<Evidence> evidences = new ArrayList<>();

        for (int number = 1; number <= 5; number++) {
            evidences.add(savedEvidence(
                    "EV-2026-0405-0" + number,
                    caseFile
            ));
        }

        Witness witness = new Witness(
                WITNESS_ID,
                caseFile,
                "테스트 목격자",
                "010-1234-5678",
                "인근 주민",
                BASE_TIME
        );

        Statement initial = new Statement(
                WITNESS_ID,
                "검은색 차량을 봤습니다.",
                StatementType.INITIAL,
                BASE_TIME
        );

        Statement correction = new Statement(
                WITNESS_ID,
                "차량 색상은 회색이었습니다.",
                StatementType.CORRECTION,
                BASE_TIME.plusHours(1)
        );

        ReflectionTestUtils.setField(initial, "statementId", 1);
        ReflectionTestUtils.setField(correction, "statementId", 2);

        InvestigationLog log = new InvestigationLog(
                caseFile,
                caseFile.getAssignedOfficer(),
                "현장 주변 CCTV를 확인했습니다.",
                BASE_TIME
        );

        ReflectionTestUtils.setField(log, "logId", 3);

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(evidences);

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(witness));

        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(WITNESS_ID)
                ))
                .thenReturn(List.of(initial, correction));

        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of(log));

        // 실행
        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        // 확인: 사건 기본 정보
        assertEquals(CASE_ID, input.caseId());
        assertEquals(caseFile.getTitle(), input.caseTitle());
        assertEquals(caseFile.getDescription(), input.caseDescription());

        // 확인: 전체 증거물의 번호·이름·설명을 빠짐없이 변환
        List<HypothesisAiInput.EvidenceInput> expectedEvidences =
                new ArrayList<>();

        for (Evidence evidence : evidences) {
            expectedEvidences.add(
                    new HypothesisAiInput.EvidenceInput(
                            evidence.getEvidenceId(),
                            evidence.getEvidenceName(),
                            evidence.getDescription()
                    )
            );
        }

        assertEquals(5, input.evidences().size());
        assertEquals(expectedEvidences, input.evidences());

        // 확인: 최초·정정 진술을 유형·일시와 함께 유지
        assertEquals(
                List.of(
                        new HypothesisAiInput.StatementInput(
                                1,
                                WITNESS_ID,
                                StatementType.INITIAL,
                                BASE_TIME,
                                "검은색 차량을 봤습니다."
                        ),
                        new HypothesisAiInput.StatementInput(
                                2,
                                WITNESS_ID,
                                StatementType.CORRECTION,
                                BASE_TIME.plusHours(1),
                                "차량 색상은 회색이었습니다."
                        )
                ),
                input.statements()
        );

        // 확인: 수사기록 번호와 내용 변환
        assertEquals(
                List.of(
                        new HypothesisAiInput.InvestigationLogInput(
                                3,
                                "현장 주변 CCTV를 확인했습니다."
                        )
                ),
                input.investigationLogs()
        );

        // 확인: 목격자의 진술을 한 번에 조회
        verify(statementRepository)
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(WITNESS_ID)
                );
    }

    @Test
    @DisplayName("증거물만 있어도 입력을 구성하고 목격자가 없으면 진술 조회를 생략한다")
    void buildInputWithEvidenceOnly() {

        CaseFile caseFile = prepareAccessibleCase();

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-01", caseFile)
                ));

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());

        // 실행
        HypothesisAiInput input =
                service.buildAiInput(CASE_ID, OFFICER_ID);

        // 확인: 한 종류의 자료만 있어도 허용
        assertEquals(1, input.evidences().size());
        assertTrue(input.statements().isEmpty());
        assertTrue(input.investigationLogs().isEmpty());

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("수사 자료가 모두 없으면 409 예외가 발생한다")
    void rejectEmptyMaterials() {

        prepareAccessibleCase();

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
        verifyNoInteractions(statementRepository);
    }

    // ==================== 로그인·권한·상태 테스트 ====================

    @Test
    @DisplayName("로그인 정보가 없으면 401 예외가 발생한다")
    void rejectMissingLogin() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verifyNoInteractions(userRepository, caseRepository);
        verifyNoMaterialQueries();
    }

    @Test
    @DisplayName("로그인 회원이 존재하지 않으면 401 예외가 발생한다")
    void rejectMissingUser() {

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verifyNoInteractions(caseRepository);
        verifyNoMaterialQueries();
    }

    @ParameterizedTest(name = "{0} 역할 접근 거부")
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "ADMIN"}
    )
    @DisplayName("경찰관이 아니면 403 예외가 발생한다")
    void rejectNonPolice(UserRole role) {

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(savedUser(OFFICER_ID, role)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(caseRepository);
        verifyNoMaterialQueries();
    }

    @Test
    @DisplayName("사건이 없으면 404 예외가 발생한다")
    void rejectMissingCase() {

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(
                        savedUser(OFFICER_ID, UserRole.POLICE)
                ));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoMaterialQueries();
    }

    @ParameterizedTest(name = "사건 담당자 ID: {0}")
    @NullSource
    @ValueSource(ints = {8})
    @DisplayName("담당자가 없거나 다른 경찰관이면 403 예외가 발생한다")
    void rejectUnassignedOrOtherOfficer(Integer assignedOfficerId) {

        CaseFile caseFile = prepareAccessibleCase();

        User assignedOfficer = assignedOfficerId == null
                ? null
                : savedUser(assignedOfficerId, UserRole.POLICE);

        // 담당자가 없거나 다른 경찰관인 상황 재현
        ReflectionTestUtils.setField(
                caseFile,
                "assignedOfficer",
                assignedOfficer
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoMaterialQueries();
    }

    @ParameterizedTest(name = "{0} 상태 접근 거부")
    @EnumSource(
            value = CaseStatus.class,
            names = {"WAITING", "CLOSED", "CANCELED"}
    )
    @DisplayName("수사중이 아닌 사건이면 409 예외가 발생한다")
    void rejectNonInvestigatingCase(CaseStatus status) {

        CaseFile caseFile = prepareAccessibleCase();

        ReflectionTestUtils.setField(caseFile, "status", status);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buildAiInput(CASE_ID, OFFICER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoMaterialQueries();
    }

    // ==================== 가설 생성 흐름 테스트 ====================

    @Test
    @DisplayName("AI에 자료를 전달하고 응답 검증 후 후보를 반환한다")
    void generateHypotheses() {

        HypothesisAiInput input = prepareGenerationInput();
        List<HypothesisCandidateResponse> candidates =
                generationCandidates();

        when(hypothesisAiClient.generate(input))
                .thenReturn(candidates);

        // 실행
        HypothesisGenerateResponse response =
                service.generateHypotheses(CASE_ID, OFFICER_ID);

        // 확인: 사건번호와 생성된 후보 반환
        assertEquals(CASE_ID, response.caseId());
        assertEquals(candidates, response.hypotheses());

        // 확인: AI 호출 다음에 입력 자료와 후보를 검증기에 전달
        InOrder order = inOrder(
                hypothesisAiClient,
                hypothesisResponseValidator
        );

        order.verify(hypothesisAiClient).generate(input);
        order.verify(hypothesisResponseValidator)
                .validate(input, candidates);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("응답 검증에 실패하면 정상 응답 대신 예외를 전달한다")
    void rejectInvalidAiResponse() {

        HypothesisAiInput input = prepareGenerationInput();

        // AI가 빈 후보 목록을 반환한 상황
        List<HypothesisCandidateResponse> candidates = List.of();

        when(hypothesisAiClient.generate(input))
                .thenReturn(candidates);

        ResponseStatusException validationException =
                new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI 분석 결과를 확인할 수 없습니다."
                );

        // 검증기가 예외를 발생시키도록 설정
        doThrow(validationException)
                .when(hypothesisResponseValidator)
                .validate(input, candidates);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        // 확인: 검증 실패를 무시하지 않고 그대로 전달
        assertSame(validationException, exception);
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());

        verify(hypothesisResponseValidator)
                .validate(input, candidates);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 응답 검증을 실행하지 않는다")
    void rejectAiCallFailure() {

        HypothesisAiInput input = prepareGenerationInput();

        ResponseStatusException aiException =
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI 서비스를 이용할 수 없습니다."
                );

        when(hypothesisAiClient.generate(input))
                .thenThrow(aiException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, OFFICER_ID)
        );

        assertSame(aiException, exception);

        // 응답을 받지 못했으므로 검증하지 않음
        verifyNoInteractions(hypothesisResponseValidator);
    }

    @Test
    @DisplayName("로그인하지 않으면 AI 호출과 응답 검증을 실행하지 않는다")
    void rejectGenerationWithoutLogin() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generateHypotheses(CASE_ID, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verifyNoInteractions(
                userRepository,
                caseRepository,
                hypothesisAiClient,
                hypothesisResponseValidator
        );

        verifyNoMaterialQueries();
    }

    // ==================== 테스트 준비·확인용 메서드 ====================

    // 로그인한 담당 경찰관과 수사중 사건을 조회하도록 설정
    private CaseFile prepareAccessibleCase() {

        User officer = savedUser(OFFICER_ID, UserRole.POLICE);

        CaseFile caseFile = new CaseFile(
                CASE_ID,
                officer,
                "테스트 사건",
                CaseType.THEFT,
                "가상 사건의 상세 내용입니다.",
                BASE_TIME,
                "테스트 장소"
        );

        // 담당자 연결과 수사중 상태 설정
        caseFile.assignOfficer(officer);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));

        return caseFile;
    }

    // 생성 테스트에서 증거물 한 건을 조회하도록 설정
    // 기대하는 AI 입력 DTO도 함께 반환
    private HypothesisAiInput prepareGenerationInput() {

        CaseFile caseFile = prepareAccessibleCase();

        Evidence evidence =
                savedEvidence("EV-2026-0405-01", caseFile);

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(evidence));

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        when(investigationLogRepository
                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(CASE_ID))
                .thenReturn(List.of());

        return new HypothesisAiInput(
                CASE_ID,
                caseFile.getTitle(),
                caseFile.getDescription(),
                List.of(
                        new HypothesisAiInput.EvidenceInput(
                                evidence.getEvidenceId(),
                                evidence.getEvidenceName(),
                                evidence.getDescription()
                        )
                ),
                List.of(),
                List.of()
        );
    }

    // AI가 반환하는 후보 역할의 테스트 데이터
    private List<HypothesisCandidateResponse> generationCandidates() {
        return List.of(
                new HypothesisCandidateResponse(
                        "테스트 가설",
                        "현장 사진을 근거로 작성한 테스트 응답입니다.",
                        "주변 자료를 추가 확인해주세요.",
                        60,
                        List.of("EV-2026-0405-01"),
                        List.of()
                )
        );
    }

    // 실제 DB 없이 회원 번호와 역할을 가진 객체 생성
    private User savedUser(Integer userId, UserRole role) {

        User user = new User(
                "test-user-" + userId,
                "test-password-hash",
                "테스트 회원",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "role", role);

        return user;
    }

    // 입력 변환에 사용할 테스트 증거물 생성
    private Evidence savedEvidence(
            String evidenceId,
            CaseFile caseFile
    ) {
        return new Evidence(
                evidenceId,
                caseFile,
                "현장 사진",
                EvidenceType.PHOTO,
                "출입구에 신발 자국이 남아 있습니다.",
                BASE_TIME,
                "현장 출입구"
        );
    }

    // 조건 검사 실패 후 수사 자료를 조회하지 않았는지 확인
    private void verifyNoMaterialQueries() {
        verifyNoInteractions(
                evidenceRepository,
                witnessRepository,
                statementRepository,
                investigationLogRepository
        );
    }
}