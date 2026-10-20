package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.response.BoardCaseResponse;
import com.polink.investigation.dto.response.BoardEvidenceSummaryResponse;
import com.polink.investigation.dto.response.BoardWitnessSummaryResponse;
import com.polink.investigation.dto.response.InvestigationBoardResponse;
import com.polink.investigation.dto.response.InvestigationLogResponse;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvestigationBoardService {

    // 수사보드의 각 영역에는 최대 4개의 요약 정보만 표시
    private static final int SUMMARY_LIMIT = 4;

    private final CaseRepository caseRepository;
    private final EvidenceRepository evidenceRepository;
    private final WitnessRepository witnessRepository;
    private final StatementRepository statementRepository;
    private final InvestigationLogRepository investigationLogRepository;
    private final UserRepository userRepository;

    public InvestigationBoardService(
            CaseRepository caseRepository,
            EvidenceRepository evidenceRepository,
            WitnessRepository witnessRepository,
            StatementRepository statementRepository,
            InvestigationLogRepository investigationLogRepository,
            UserRepository userRepository
    ) {
        this.caseRepository = caseRepository;
        this.evidenceRepository = evidenceRepository;
        this.witnessRepository = witnessRepository;
        this.statementRepository = statementRepository;
        this.investigationLogRepository = investigationLogRepository;
        this.userRepository = userRepository;
    }

    // 사건별 수사보드 통합 조회
    @Transactional(readOnly = true)
    public InvestigationBoardResponse findInvestigationBoard(
            String caseId,
            Integer loggedInUserId
    ) {
        // 임시 사용자 번호로 로그인 회원을 확인하고, 없으면 401 처리
        User user = findLoggedInUser(loggedInUserId);

        // 시민과 관리자는 수사보드에 접근할 수 없으며 경찰관만 조회 가능
        if (user.getRole() != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사보드를 조회할 권한이 없습니다."
            );
        }

        // 수사보드에 표시할 사건 기본 정보를 조회하고, 사건이 없으면 404 처리
        CaseFile caseFile =
                caseRepository.findById(caseId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "사건 정보를 확인할 수 없습니다."
                        ));

        User assignedOfficer = caseFile.getAssignedOfficer();

        if (assignedOfficer == null
                || !assignedOfficer.getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "수사보드에 접근할 권한이 없습니다."
            );
        }

        // CaseFile Entity를 수사보드 사건 정보 DTO로 변환
        BoardCaseResponse caseInfo = BoardCaseResponse.from(caseFile);

        // 해당 사건의 전체 증거물을 조회하고 정렬 가능한 목록으로 복사
        List<Evidence> evidences =
                new ArrayList<>(evidenceRepository.findByCaseFile_CaseId(caseId));

        // 증거번호가 빠른 순서대로 수사보드에 표시
        evidences.sort(
                Comparator.comparing(Evidence::getEvidenceId)
        );

        // 목록을 최대 4개로 제한하기 전에 전체 증거물 개수를 보관
        int totalEvidenceCount = evidences.size();

        // 정렬된 증거물 중 최대 4개를 수사보드용 응답으로 변환
        List<BoardEvidenceSummaryResponse> evidenceResponses =
                createEvidenceSummaries(evidences);

        // 해당 사건의 전체 목격자를 조회하고 정렬 가능한 목록으로 복사
        List<Witness> witnesses =
                new ArrayList<>(witnessRepository.findByCaseFile_CaseId(caseId));

        // 목격자를 등록 일시 오름차순으로 정렬하고,
        // 등록 일시가 같으면 목격자 번호로 순서를 결정
        witnesses.sort(
                Comparator.comparing(Witness::getCreatedAt)
                        .thenComparing(Witness::getWitnessId)
        );

        // 목록을 최대 4명으로 제한하기 전에 전체 목격자 수를 보관
        int totalWitnessCount = witnesses.size();

        // 최대 4명의 목격자에게 진술 개수와 최신 진술을 연결
        List<BoardWitnessSummaryResponse> witnessResponses =
                createWitnessSummaries(witnesses);

        // 해당 사건의 전체 수사기록을 수사 일시·기록 번호 오름차순으로 조회
        // 수사보드용 최신순 정렬은 요약 변환 메서드에서 별도로 수행
        List<InvestigationLog> investigationLogs =
                new ArrayList<>(
                        investigationLogRepository
                                .findByCaseFile_CaseIdOrderByInvestigatedAtAscLogIdAsc(
                                        caseId
                                )
                );

        // 목록을 최대 4개로 제한하기 전에 전체 수사기록 개수를 보관
        int totalInvestigationLogCount = investigationLogs.size();

        // 전체 수사기록 중 최신 기록 최대 4개를 응답으로 변환
        List<InvestigationLogResponse> investigationLogResponses =
                createInvestigationLogSummaries(investigationLogs);

        // 사건 기본 정보와 각 수사 자료의 전체 개수·요약 목록을 하나로 구성
        return new InvestigationBoardResponse(
                caseInfo,
                totalEvidenceCount,
                evidenceResponses,
                totalWitnessCount,
                witnessResponses,
                totalInvestigationLogCount,
                investigationLogResponses
        );
    }

    // 수사보드에 표시할 증거물 최대 4개 구성
    private List<BoardEvidenceSummaryResponse> createEvidenceSummaries(
            List<Evidence> evidences
    ) {
        List<BoardEvidenceSummaryResponse> responses =
                new ArrayList<>();

        // 실제 증거물 수와 표시 제한 4개 중 작은 값을 선택
        int displayCount = Math.min(
                SUMMARY_LIMIT,
                evidences.size()
        );

        // 정렬된 목록의 앞에서부터 최대 4개를 요약 DTO로 변환
        for (int index = 0; index < displayCount; index++) {
            responses.add(
                    BoardEvidenceSummaryResponse.from(
                            evidences.get(index)
                    )
            );
        }

        return responses;
    }

    // 수사보드에 표시할 목격자 최대 4명과 최신 진술 구성
    private List<BoardWitnessSummaryResponse> createWitnessSummaries(
            List<Witness> witnesses
    ) {
        int displayCount = Math.min(
                SUMMARY_LIMIT,
                witnesses.size()
        );

        // 정렬된 전체 목격자 중 수사보드에 표시할 최대 4명 선택
        List<Witness> displayedWitnesses =
                witnesses.subList(0, displayCount);

        // 목격자가 없으면 진술을 조회하지 않고 빈 목록 반환
        if (displayedWitnesses.isEmpty()) {
            return List.of();
        }

        // 선택한 목격자들의 진술을 한 번에 조회하기 위해 목격자 번호 수집
        List<String> witnessIds = new ArrayList<>();

        for (Witness witness : displayedWitnesses) {
            witnessIds.add(witness.getWitnessId());
        }

        // 목격자별 반복 조회를 피하기 위해 최대 4명의 진술을 한 번에 조회
        // 진술은 등록 일시·진술 번호 오름차순으로 반환
        List<Statement> statements =
                statementRepository
                        .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                                witnessIds
                        );

        // 조회된 전체 진술을 목격자 번호별 목록으로 분류
        Map<String, List<Statement>> statementsByWitness =
                new HashMap<>();

        // 해당 목격자의 진술 목록이 없으면 새 목록을 만든 후 현재 진술 추가
        for (Statement statement : statements) {
            statementsByWitness
                    .computeIfAbsent(
                            statement.getWitnessId(),
                            key -> new ArrayList<>()
                    )
                    .add(statement);
        }

        List<BoardWitnessSummaryResponse> responses =
                new ArrayList<>();

        // 현재 목격자의 진술 목록을 가져오고, 진술이 없으면 빈 목록 사용
        for (Witness witness : displayedWitnesses) {
            List<Statement> witnessStatements =
                    statementsByWitness.getOrDefault(
                            witness.getWitnessId(),
                            List.of()
                    );

            // 진술은 오래된 순서로 정렬되어 있으므로 마지막 항목이 최신 진술
            Statement latestStatement = null;

            if (!witnessStatements.isEmpty()) {
                latestStatement =
                        witnessStatements.get(
                                witnessStatements.size() - 1
                        );
            }

            // 목격자 정보에 전체 진술 개수와 최신 진술을 연결해 응답 구성
            responses.add(
                    BoardWitnessSummaryResponse.from(
                            witness,
                            latestStatement,
                            witnessStatements.size()
                    )
            );
        }

        return responses;
    }

    // 수사보드에 표시할 최신 수사기록 최대 4개 구성
    private List<InvestigationLogResponse>
    createInvestigationLogSummaries(
            List<InvestigationLog> investigationLogs
    ) {
        // 수사보드에는 최신 기록이 먼저 보이도록 수사 일시 내림차순 정렬
    // 수사 일시가 같다면 기록 번호가 큰 기록을 먼저 표시
        investigationLogs.sort(
                Comparator.comparing(
                                InvestigationLog::getInvestigatedAt
                        )
                        .thenComparing(
                                InvestigationLog::getLogId
                        )
                        .reversed()
        );

        List<InvestigationLogResponse> responses =
                new ArrayList<>();

        // 실제 수사기록 수와 표시 제한 4개 중 작은 값을 선택
        int displayCount = Math.min(
                SUMMARY_LIMIT,
                investigationLogs.size()
        );

        // 최신 기록부터 최대 4개를 응답 DTO로 변환
        for (int index = 0; index < displayCount; index++) {
            responses.add(
                    InvestigationLogResponse.from(
                            investigationLogs.get(index)
                    )
            );
        }

        return responses;
    }

    // 임시 헤더로 전달받은 로그인 사용자 확인
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
}