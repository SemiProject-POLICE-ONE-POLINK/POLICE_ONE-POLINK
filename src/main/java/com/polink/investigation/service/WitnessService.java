package com.polink.investigation.service;

import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.CaseExistenceRepository;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class WitnessService {

    // 목격자 저장 및 사건별 목격자 조회
    private final WitnessRepository witnessRepository;

    // 최초·추가 진술 저장
    private final StatementRepository statementRepository;

    // 목격자를 등록할 사건의 존재 여부 확인
    private final CaseExistenceRepository caseExistenceRepository;

    public WitnessService(WitnessRepository witnessRepository, StatementRepository statementRepository, CaseExistenceRepository caseExistenceRepository) {
        this.witnessRepository = witnessRepository;
        this.statementRepository = statementRepository;
        this.caseExistenceRepository = caseExistenceRepository;
    }

    @Transactional
    public WitnessCreateResponse createWitness(
            String caseId,
            WitnessCreateRequest request
    ) {
        // 1. 목격자를 등록할 사건이 있는지 확인
        if (!caseExistenceRepository.existsByCaseId(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "사건 정보를 확인할 수 없어 목격자를 등록할 수 없습니다."
            );
        }

        // 2. 같은 사건에 등록된 목격자를 조회해 다음 목격자 번호 생성
        List<Witness> savedWitnesses =
                witnessRepository.findByCaseId(caseId);

        String witnessId = generateWitnessId(caseId, savedWitnesses);

        // 목격자와 최초 진술에 동일한 등록 일시 사용
        LocalDateTime createdAt = LocalDateTime.now();

        // 3. 입력받은 목격자 정보로 Entity 생성
        Witness witness = new Witness(
                witnessId,
                caseId,
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
                "INITIAL",
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
