package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.StatementResponse;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.dto.response.WitnessListResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WitnessService {

    // 목격자 저장 및 사건별 목격자 조회
    private final WitnessRepository witnessRepository;

    // 최초·추가 진술 저장
    private final StatementRepository statementRepository;

    // 목격자를 등록할 사건의 존재 여부 확인
    private final InvestigationAccessService investigationAccessService;

    public WitnessService(WitnessRepository witnessRepository, StatementRepository statementRepository, InvestigationAccessService investigationAccessService) {
        this.witnessRepository = witnessRepository;
        this.statementRepository = statementRepository;
        this.investigationAccessService = investigationAccessService;
    }

    // 사건별 목격자 정보 및 최초 진술 추가
    @Transactional
    public WitnessCreateResponse createWitness(
            String caseId,
            Integer loggedInUserId,
            WitnessCreateRequest request
    ) {
        // 로그인한 사용자가 수사중인 사건의 담당 경찰인지 확인
        CaseFile caseFile =
                investigationAccessService.findInvestigatingCase(
                        caseId,
                        loggedInUserId
                );

        // 2. 같은 사건에 등록된 목격자를 조회해 다음 목격자 번호 생성
        List<Witness> savedWitnesses =
                witnessRepository.findByCaseFile_CaseId(caseId);

        String witnessId = generateWitnessId(caseId, savedWitnesses);

        // 목격자와 최초 진술에 동일한 등록 일시 사용
        LocalDateTime createdAt = LocalDateTime.now();

        // 3. 입력받은 목격자 정보로 Entity 생성
        Witness witness = new Witness(
                witnessId,
                caseFile,
                request.name(),
                request.phone(),
                request.relationship(),
                createdAt
        );

        // 4. 진술에서 참조할 목격자를 DB에 저장
        Witness savedWitness = witnessRepository.saveAndFlush(witness);

        // 5. 저장된 목격자 번호에 연결된 최초 진술 생성
        // 목격자 등록 시 함께 입력한 진술을 최초 진술로 저장
        Statement statement = new Statement(
                savedWitness.getWitnessId(),
                request.content(),
                StatementType.INITIAL,
                createdAt
        );

        // 6. 최초 진술 저장 — 진술 번호는 DB에서 자동 생성
        Statement savedStatement = statementRepository.save(statement);

        // 7. 저장된 목격자와 최초 진술을 응답 DTO로 변환
        return WitnessCreateResponse.from(
                savedWitness,
                savedStatement
        );
    }


    // 사건별 목격자 정보와 진술 이력을 함께 조회
    @Transactional(readOnly = true)
    public List<WitnessListResponse> findWitnessList(
            String caseId,
            Integer loggedInUserId
    ) {
        // 사건 상태와 관계없이 해당 사건의 담당 경찰인지 확인
        investigationAccessService.findReadableCase(
                caseId,
                loggedInUserId
        );

        // 2. 해당 사건의 목격자 목록 조회
        List<Witness> witnesses = new ArrayList<>(
                witnessRepository.findByCaseFile_CaseId(caseId)
        );

        // 목격자가 없으면 빈 목록 반환
        if (witnesses.isEmpty()) {
            return List.of();
        }

        // 3. 목격자 등록 일시 오름차순 정렬
        // 등록 일시가 같으면 목격자 번호 오름차순
        witnesses.sort(
                Comparator.comparing(Witness::getCreatedAt)
                        .thenComparing(Witness::getWitnessId)
        );

        // 4. 진술 조회에 사용할 목격자 번호 목록 구성
        List<String> witnessIds = new ArrayList<>();

        for (Witness witness : witnesses) {
            witnessIds.add(witness.getWitnessId());
        }

        // 5. 해당 목격자들의 진술을 등록 일시·진술 번호 오름차순으로 조회
        List<Statement> statements =
                statementRepository
                        .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                                witnessIds
                        );

        /* 6. 목격자 번호별로 진술 DTO 목록을 보관
        *  순서 유지 없이 목격자 번호로 진술 목록을 빠르게 찾기 위해 HashMap 사용
        * */
        Map<String, List<StatementResponse>> statementsByWitness =
                new HashMap<>();

        for (Statement statement : statements) {
            String witnessId = statement.getWitnessId();

            // 해당 목격자의 목록이 없으면 생성
            if (!statementsByWitness.containsKey(witnessId)) {
                statementsByWitness.put(witnessId, new ArrayList<>());
            }

            // 진술을 DTO로 변환해 해당 목격자의 진술 목록에 추가
            statementsByWitness.get(witnessId)
                    .add(StatementResponse.from(statement));
        }

        // 7. 각 목격자 정보에 해당 목격자의 진술 목록을 연결
        List<WitnessListResponse> responses = new ArrayList<>();

        for (Witness witness : witnesses) {

            // 연결된 진술이 없으면 빈 목록 사용
            List<StatementResponse> witnessStatements =
                    statementsByWitness.getOrDefault(
                            witness.getWitnessId(),
                            List.of()
                    );

            responses.add(
                    WitnessListResponse.from(
                            witness,
                            witnessStatements
                    )
            );
        }

        return responses;
    }

    private String generateWitnessId(
            String caseId,
            List<Witness> savedWitnesses
    ) {

        // 사건번호에서 "SMPA-"를 제외하고 접두사 생성
        // ex) SMPA-2026-0405 → W-2026-0405-
        String prefix = "W-" + caseId.substring(5) + "-";

        int lastNumber = 0;

        for (Witness witness : savedWitnesses) {
            String witnessId = witness.getWitnessId();

            // 마지막 "-" 뒤의 순번 추출
            // 예: W-2026-0405-03 → "03"
            String numberText = witnessId.substring(
                    witnessId.lastIndexOf("-") + 1
            );

            int number = Integer.parseInt(numberText);

            // 조회 순서와 관계없이 가장 큰 순번 보관
            lastNumber = Math.max(lastNumber, number);
        }

        // 순번 두 자리의 최대값을 넘으면 등록 중단
        if (lastNumber >= 99) {
            throw new IllegalStateException(
                    "해당 사건에 등록할 수 있는 목격자 수를 초과했습니다."
            );
        }

        /* 마지막 순번에 1을 더해 다음 순번 생성 ex) W-2026-0405-04
         *  %02d: 순번을 두 자리로 표시(1 → 01)
         *  Locale.ROOT: 언어 설정과 관계없이 0~9 사용
         * */
        return prefix
                + String.format(Locale.ROOT, "%02d", lastNumber + 1);
    }
}
