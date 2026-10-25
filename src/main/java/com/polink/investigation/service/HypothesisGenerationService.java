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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

// 가설 생성 Service
@Service
public class HypothesisGenerationService {

    // AI 입력 자료를 전달하고 가설 후보를 받는 객체(테스트용)
    private final HypothesisAiClient hypothesisAiClient;

    // 경찰관이 채택하기 전까지 생성된 후보를 임시 보관
    private final HypothesisCandidateStore hypothesisCandidateStore;

    private final HypothesisRepository hypothesisRepository;

    private final HypothesisHistoryStore hypothesisHistoryStore;

    // 사건 자료를 DB에서 읽는 동안에만 사용할 읽기 전용 트랜잭션
    private final TransactionTemplate readOnlyTransaction;

    // AI가 반환한 가설의 필수값과 근거 번호 검증
    private final HypothesisResponseValidator hypothesisResponseValidator;

    // 검증된 AI 가설 후보의 자료 부합도 점수 계산
    private final HypothesisConfidenceCalculator hypothesisConfidenceCalculator;

    // 사건 기본 정보와 담당 경찰 확인
    private final CaseRepository caseRepository;

    // 로그인 사용자와 역할 확인
    private final UserRepository userRepository;

    // 사건에 등록된 증거물 조회
    private final EvidenceRepository evidenceRepository;

    // 사건에 등록된 목격자 조회
    private final WitnessRepository witnessRepository;

    // 목격자들의 진술 조회
    private final StatementRepository statementRepository;

    // 사건의 수사기록 조회
    private final InvestigationLogRepository investigationLogRepository;

    public HypothesisGenerationService(HypothesisAiClient hypothesisAiClient, HypothesisCandidateStore hypothesisCandidateStore, HypothesisRepository hypothesisRepository, HypothesisHistoryStore hypothesisHistoryStore, PlatformTransactionManager transactionManager, HypothesisResponseValidator hypothesisResponseValidator, HypothesisConfidenceCalculator hypothesisConfidenceCalculator, CaseRepository caseRepository, UserRepository userRepository, EvidenceRepository evidenceRepository, WitnessRepository witnessRepository, StatementRepository statementRepository, InvestigationLogRepository investigationLogRepository) {
        this.hypothesisAiClient = hypothesisAiClient;
        this.hypothesisCandidateStore = hypothesisCandidateStore;
        this.hypothesisRepository = hypothesisRepository;
        this.hypothesisHistoryStore = hypothesisHistoryStore;
        // DB 조회 시에만 사용할 트랜잭션 도구 생성
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.hypothesisConfidenceCalculator = hypothesisConfidenceCalculator;
        this.readOnlyTransaction.setReadOnly(true);
        this.hypothesisResponseValidator = hypothesisResponseValidator;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.evidenceRepository = evidenceRepository;
        this.witnessRepository = witnessRepository;
        this.statementRepository = statementRepository;
        this.investigationLogRepository = investigationLogRepository;
    }

    // 사건 자료를 바탕으로 가설 후보 생성 — DB에 저장하지 않음(가설 후보로 테스트)
    public HypothesisGenerateResponse generateHypotheses(
            String caseId,
            Integer loggedInUserId
    ) {

        // 담당 경찰·사건 상태를 확인하고 DB 자료를 읽는 동안만 트랜잭션 사용
        HypothesisAiInput input = readOnlyTransaction.execute(
                status -> buildAiInput(caseId, loggedInUserId)
        );

        // AI에서 점수가 없는 원본 후보를 받기
        List<HypothesisAiCandidate> aiCandidates =
                hypothesisAiClient.generate(input);

        // 후보의 필수값과 근거 번호를 먼저 검사
        hypothesisResponseValidator.validate(input, aiCandidates);

        // 이전 가설 및 이번 응답 안에서 같은 문장의 후보 제외
        List<HypothesisAiCandidate> newCandidates =
                filterNewCandidates(input, aiCandidates);

        if (newCandidates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "새로운 수사 가설을 도출하지 못했습니다. "
                            + "기존 가설을 검토하거나 수사 자료를 추가한 후 다시 분석해주세요."
            );
        }

        // 검증된 후보마다 서버가 점수를 계산해 화면용 응답으로 변환
        List<HypothesisCandidateResponse> responses = new ArrayList<>();

        // 같은 분석에서 생성된 후보들을 묶을 분석 번호 생성
        String analysisId = UUID.randomUUID().toString();

        for (HypothesisAiCandidate candidate : newCandidates) {

            // 검증된 원본 후보의 점수를 서버에서 계산
            int confidence = hypothesisConfidenceCalculator.calculate(candidate);

            // 원본 후보와 계산된 점수를 15분 동안 보관하고 임시 번호를 받음
            String candidateId = hypothesisCandidateStore.put(
                    analysisId,
                    input.caseId(),
                    loggedInUserId,
                    candidate,
                    confidence
            );

            responses.add(
                    new HypothesisCandidateResponse(
                            candidateId,
                            candidate.content(),
                            candidate.reason(),
                            candidate.additionalRecommendation(),
                            confidence,
                            candidate.evidenceIds(),
                            candidate.statementIds(),
                            candidate.logIds(),
                            candidate.opposingEvidenceIds(),
                            candidate.opposingStatementIds(),
                            candidate.circumstances()
                    )
            );
        }

        // 채택·기각·미선택 여부와 관계없이 생성 이력 보관
        hypothesisHistoryStore.remember(
                caseId,
                loggedInUserId,
                newCandidates
        );

        // 후보는 DB에 저장하지 않고 사건번호와 함께 반환
        return new HypothesisGenerateResponse(
                input.caseId(),
                responses
        );
    }

    // 권한을 확인한 사건의 수사 자료를 AI 입력 DTO로 구성
    @Transactional(readOnly = true)
    public HypothesisAiInput buildAiInput(
            String caseId,
            Integer loggedInUserId
    ) {
        // 로그인한 사용자가 수사중인 사건의 담당 경찰인지 확인
        CaseFile caseFile = findAccessibleCase(caseId, loggedInUserId);

        // 2. 사건의 증거물을 조회해 AI 입력용으로 변환
        List<Evidence> evidences = evidenceRepository.findByCaseFile_CaseId(caseId);

        List<HypothesisAiInput.EvidenceInput> evidenceInputs = new ArrayList<>();

        for (Evidence evidence : evidences) {
            evidenceInputs.add(
                    new HypothesisAiInput.EvidenceInput(
                            evidence.getEvidenceId(),
                            evidence.getEvidenceName(),
                            evidence.getDescription()
                    )
            );
        }

        // 3. 해당 사건의 목격자 번호 수집
        List<Witness> witnesses =
                witnessRepository.findByCaseFile_CaseId(caseId);

        List<String> witnessIds = new ArrayList<>();

        for (Witness witness : witnesses) {
            witnessIds.add(witness.getWitnessId());
        }

        // 4. 목격자들의 전체 진술을 조회해 AI 입력용으로 변환
        List<HypothesisAiInput.StatementInput> statementInputs = new ArrayList<>();

        // 목격자가 없으면 진술 조회 생략
        if (!witnessIds.isEmpty()) {
            List<Statement> statements =
                    statementRepository
                            .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                                    witnessIds
                            );

            for (Statement statement : statements) {
                statementInputs.add(
                        new HypothesisAiInput.StatementInput(
                                statement.getStatementId(),
                                statement.getWitnessId(),
                                statement.getStatementType(),
                                statement.getCreatedAt(),
                                statement.getContent()
                        )
                );
            }
        }
        // 5. 사건의 수사기록을 조회해 AI 입력용으로 변환
        List<InvestigationLog> investigationLogs =
                investigationLogRepository
                        .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(
                                caseId
                        );

        List<HypothesisAiInput.InvestigationLogInput> logInputs =
                new ArrayList<>();

        for (InvestigationLog investigationLog : investigationLogs) {
            logInputs.add(
                    new HypothesisAiInput.InvestigationLogInput(
                            investigationLog.getLogId(),
                            investigationLog.getContent(),
                            investigationLog.getInvestigatedAt()
                    )
            );
        }

        // 6. 증거물·진술·수사기록이 모두 없으면 가설 생성 중단
        if (evidenceInputs.isEmpty()
                && statementInputs.isEmpty()
                && logInputs.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "분석할 수사 자료가 없습니다. 증거물·진술·수사기록 중 하나 이상을 등록한 후 다시 시도해주세요."
            );
        }

        // 7. 이전 생성 이력을 가져오고 같은 문장은 한 번만 포함
        Set<String> previousHypotheses = new LinkedHashSet<>(
                hypothesisHistoryStore.getHistory(
                        caseId,
                        loggedInUserId
                )
        );

        // 8. DB에 채택된 가설도 중복 회피 목록에 포함
        List<Hypothesis> adoptedHypotheses =
                hypothesisRepository.findAdoptedByCaseId(caseId);

        for (Hypothesis hypothesis : adoptedHypotheses) {
            String content = hypothesis.getContent();

            if (content != null && !content.isBlank()) {
                previousHypotheses.add(content.strip());
            }
        }

        // 9. 사건 기본 정보와 전체 수사 자료를 하나의 DTO로 구성
        return new HypothesisAiInput(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getDescription(),
                evidenceInputs,
                statementInputs,
                logInputs,
                List.copyOf(previousHypotheses)
        );
    }

    // 이전 가설이나 이번 응답 안에서 같은 문장이 반복되면 제외
    private List<HypothesisAiCandidate> filterNewCandidates(
            HypothesisAiInput input,
            List<HypothesisAiCandidate> candidates
    ) {
        Set<String> seenContents = new HashSet<>();

        for (String previous : input.previousHypotheses()) {
            seenContents.add(normalizeContent(previous));
        }

        List<HypothesisAiCandidate> newCandidates =
                new ArrayList<>();

        for (HypothesisAiCandidate candidate : candidates) {
            String content = normalizeContent(candidate.content());

            // 이미 있는 문장이면 add()가 false를 반환
            if (seenContents.add(content)) {
                newCandidates.add(candidate);
            }
        }

        return newCandidates;
    }

    // 앞뒤 공백·연속 공백·영문 대소문자 차이를 정리해 비교
    private String normalizeContent(String content) {
        return content
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    // 로그인한 사용자가 해당 사건의 담당 경찰인지 확인 후 사건 정보를 반환
    private CaseFile findAccessibleCase(
            String caseId,
            Integer loggedInUserId
    ) {

        // 1. 로그인 정보가 없으면 분석 불가
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 2. 로그인한 회원이 존재하는지 확인
        User user = userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 정보를 확인할 수 없습니다."
                ));

        // 3. 경찰만 가설 생성 가능
        if (user.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사 가설을 생성할 권한이 없습니다."
            );
        }

        // 4. 분석할 사건이 존재하는지 확인
        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건 정보를 확인할 수 없습니다."
                ));

        // 5. 담당 경찰이 없거나 로그인한 경찰관과 다르면 분석 불가
        User assignedOfficer = caseFile.getAssignedOfficer();

        if (assignedOfficer == null
                || !assignedOfficer.getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 사건의 담당 경찰만 수사 가설을 생성할 수 있습니다."
            );
        }

        // 6. 수사중인 사건에서만 가설 생성 허용
        if (caseFile.getStatus() != CaseStatus.INVESTIGATING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "수사중인 사건에서만 가설을 생성할 수 있습니다."
            );
        }

        // TODO: 별도 수사 상태 구현 후 수사중 여부도 확인
        // 사건이 수사중이어도 수사가 종결되었다면 가설 생성 거부
        return caseFile;

    }
}
