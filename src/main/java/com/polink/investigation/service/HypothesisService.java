package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

// 가설 채택·저장 담당 Service
@Service
public class HypothesisService {

    private final HypothesisCandidateStore hypothesisCandidateStore;
    private final HypothesisRepository hypothesisRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final EvidenceRepository evidenceRepository;
    private final StatementRepository statementRepository;
    private final WitnessRepository witnessRepository;
    private final ObjectMapper objectMapper;

    public HypothesisService(HypothesisCandidateStore hypothesisCandidateStore, HypothesisRepository hypothesisRepository, CaseRepository caseRepository, UserRepository userRepository, EvidenceRepository evidenceRepository, StatementRepository statementRepository, WitnessRepository witnessRepository, ObjectMapper objectMapper) {
        this.hypothesisCandidateStore = hypothesisCandidateStore;
        this.hypothesisRepository = hypothesisRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.evidenceRepository = evidenceRepository;
        this.statementRepository = statementRepository;
        this.witnessRepository = witnessRepository;
        this.objectMapper = objectMapper;
    }

    // 경찰관이 AI 가설 후보를 채택하여 저장
    @Transactional
    public HypothesisResponse adoptHypothesis(
            String caseId,
            Integer loggedInUserId,
            HypothesisSaveRequest request
    ) {
        User officer = findLoggedInUser(loggedInUserId);

        // 사용자 중 경찰만 채택 권한 부여
        if (officer.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사 가설을 채택할 권한이 없습니다."
            );
        }
        // 사건 존재 여부 확인
        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건 정보를 확인할 수 없습니다."
                ));

        // 미배정 사건이나 다른 경찰이 담당하는 사건에는 접근 불가
        User assignedOfficer = caseFile.getAssignedOfficer();
        if (assignedOfficer == null
                || !assignedOfficer.getUserId().equals(officer.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "이 사건의 수사 가설을 채택할 권한이 없습니다."
            );
        }

        // 수사중인 사건만 가설 채택 가능
        if (caseFile.getStatus() != CaseStatus.INVESTIGATING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "수사 중인 사건에서만 가설을 채택할 수 있습니다."
            );
        }

        // 요청에 담긴 임시 번호로 서버에 보관된 원본 후보 조회
        HypothesisCandidateStore.StoredCandidate stored =
                hypothesisCandidateStore.find(request.candidateId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "선택한 가설 후보를 확인할 수 없습니다. 다시 생성해 주세요."
                        ));

        // 다른 사건이나 다른 경찰에게 생성된 후보는 채택할 수 없음
        if (!stored.caseId().equals(caseId)
                || !stored.officerId().equals(officer.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "이 가설 후보를 채택할 권한이 없습니다."
            );
        }
        // 화면이 보낸 내용이 아니라 서버에 보관한 AI 원본 후보를 사용
        HypothesisAiCandidate candidate = stored.candidate();

        List<String> evidenceIds = candidate.evidenceIds();
        List<Integer> statementIds = candidate.statementIds();
        List<String> circumstances = candidate.circumstances();

        validateEvidenceIds(caseId, evidenceIds);
        validateStatementIds(caseId, statementIds);

        HypothesisBasis basis = new HypothesisBasis(
                evidenceIds,
                statementIds,
                circumstances
        );

        String basisJson = writeBasisJson(basis);

        Hypothesis hypothesis = new Hypothesis(
                caseFile,
                officer,
                candidate.content(),
                candidate.reason(),
                candidate.additionalRecommendation(),
                stored.confidence(),
                basisJson
        );

        Hypothesis saved = hypothesisRepository.save(hypothesis);

        // DB 트랜잭션이 정상 완료된 뒤에만 임시 후보 제거
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        hypothesisCandidateStore.remove(request.candidateId());
                    }
                }
        );

        return new HypothesisResponse(
                saved.getHypothesisId(),
                saved.getCaseFile().getCaseId(),
                saved.getContent(),
                saved.getReason(),
                saved.getAdditionalRecommendation(),
                saved.getConfidence(),
                saved.getSource(),
                basis,
                saved.getAdoptedBy().getUserId(),
                saved.getAdoptedBy().getName(),
                saved.getAdoptedAt()
        );
    }

    // 인증된 회원 번호로 사용자 조회
    private User findLoggedInUser(Integer loggedInUserId) {
        if (loggedInUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        return userRepository.findById(loggedInUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "로그인 정보를 확인할 수 없습니다."
                ));
    }

    // 근거 증거물이 존재하고 현재 사건에 속하는지 확인
    private void validateEvidenceIds(
            String caseId,
            List<String> evidenceIds
    ) {
        for (String evidenceId : evidenceIds) {
            if (evidenceId == null || evidenceId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "근거 증거물 번호가 올바르지 않습니다."
                );
            }

            Evidence evidence = evidenceRepository.findById(evidenceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "근거 증거물 정보를 확인할 수 없습니다."
                    ));

            if (!caseId.equals(evidence.getCaseFile().getCaseId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "다른 사건의 증거물은 근거로 사용할 수 없습니다."
                );
            }
        }
    }

    // 근거 진술이 존재하고 현재 사건 목격자의 진술인지 확인
    private void validateStatementIds(
            String caseId,
            List<Integer> statementIds
    ) {
        for (Integer statementId : statementIds) {
            if (statementId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "근거 진술 정보가 누락되었습니다."
                );
            }

            Statement statement = statementRepository.findById(statementId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "근거 진술 정보를 확인할 수 없습니다."
                    ));

            Witness witness = witnessRepository
                    .findById(statement.getWitnessId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "근거 진술의 목격자 정보를 확인할 수 없습니다."
                    ));

            if (!caseId.equals(witness.getCaseFile().getCaseId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "다른 사건의 진술은 근거로 사용할 수 없습니다."
                );
            }
        }

    }

    // 근거 번호 목록을 basis_json 컬럼에 저장할 문자열로 변환
    private String writeBasisJson(HypothesisBasis basis) {
        try {
            return objectMapper.writeValueAsString(basis);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "가설 정보 저장에 실패하였습니다..", exception
            );
        }
    }

    // 담당 경찰이 사건에 저장된 가설을 최신 채택순으로 조회
    @Transactional(readOnly = true)
    public List<HypothesisResponse> findAdoptedHypotheses(
            String caseId,
            Integer loggedInUserId
    ) {
        User officer = findLoggedInUser(loggedInUserId);

        if (officer.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사 가설을 조회할 권한이 없습니다."
            );
        }

        CaseFile caseFile = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "사건 정보를 확인할 수 없습니다."
                ));

        User assignedOfficer = caseFile.getAssignedOfficer();
        if (assignedOfficer == null
                || !assignedOfficer.getUserId().equals(officer.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "이 사건의 수사 가설을 조회할 권한이 없습니다."
            );
        }

        List<Hypothesis> hypotheses =
                hypothesisRepository.findAdoptedByCaseId(caseId);

        List<HypothesisResponse> responses = new ArrayList<>();

        for (Hypothesis hypothesis : hypotheses) {
            responses.add(new HypothesisResponse(
                    hypothesis.getHypothesisId(),
                    hypothesis.getCaseFile().getCaseId(),
                    hypothesis.getContent(),
                    hypothesis.getReason(),
                    hypothesis.getAdditionalRecommendation(),
                    hypothesis.getConfidence(),
                    hypothesis.getSource(),
                    readBasisJson(hypothesis.getBasisJson()),
                    hypothesis.getAdoptedBy().getUserId(),
                    hypothesis.getAdoptedBy().getName(),
                    hypothesis.getAdoptedAt()
            ));
        }

        return responses;
    }

    // DB에 저장된 근거 번호 JSON을 응답 DTO로 복원
    private HypothesisBasis readBasisJson(String basisJson) {
        if (basisJson == null || basisJson.isBlank()) {
            return new HypothesisBasis(List.of(), List.of(), List.of());
        }

        try {
            return objectMapper.readValue(basisJson, HypothesisBasis.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "저장된 가설의 근거 정보를 읽을 수 없습니다.",
                    exception
            );
        }
    }
}
