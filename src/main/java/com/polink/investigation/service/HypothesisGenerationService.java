package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import com.polink.investigation.dto.response.HypothesisGenerateResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.EvidenceRepository;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

// 가설 생성 Service
@Service
public class HypothesisGenerationService {

    // AI 입력 자료를 전달하고 가설 후보를 받는 객체(테스트용)
    private final HypothesisAiClient hypothesisAiClient;

    // AI가 반환한 가설의 필수값과 근거 번호 검증
    private final HypothesisResponseValidator hypothesisResponseValidator;

    // 사건 기본 정보와 담당 경찰관 확인
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

    public HypothesisGenerationService(HypothesisAiClient hypothesisAiClient, HypothesisResponseValidator hypothesisResponseValidator, CaseRepository caseRepository, UserRepository userRepository, EvidenceRepository evidenceRepository, WitnessRepository witnessRepository, StatementRepository statementRepository, InvestigationLogRepository investigationLogRepository) {
        this.hypothesisAiClient = hypothesisAiClient;
        this.hypothesisResponseValidator = hypothesisResponseValidator;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.evidenceRepository = evidenceRepository;
        this.witnessRepository = witnessRepository;
        this.statementRepository = statementRepository;
        this.investigationLogRepository = investigationLogRepository;
    }

    // 사건 자료를 바탕으로 가설 후보 생성 — DB에 저장하지 않음(가짜 후보로 테스트)
    @Transactional(readOnly = true)
    public HypothesisGenerateResponse generateHypotheses(
            String caseId,
            Integer loggedInUserId
    ) {

        // 권한·사건 상태를 확인하고 AI 입력 자료 구성
        HypothesisAiInput input = buildAiInput(caseId, loggedInUserId);

        // AI에 입력 자료를 전달하고 가설 후보 받기
        List<HypothesisCandidateResponse> hypotheses =
                hypothesisAiClient.generate(input);

        // 필수값·가설 개수·신뢰도·근거 번호 검증
        hypothesisResponseValidator.validate(input, hypotheses);

        // 사건번호와 생성된 후보 목록 반환
        return new HypothesisGenerateResponse(
                input.caseId(),
                hypotheses
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
                            investigationLog.getContent()
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

        // 7. 사건 기본 정보와 전체 수사 자료를 하나의 DTO로 구성
        return new HypothesisAiInput(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getDescription(),
                evidenceInputs,
                statementInputs,
                logInputs
        );
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

        // 5. 담당 경찰관이 없거나 로그인한 경찰관과 다르면 분석 불가
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
