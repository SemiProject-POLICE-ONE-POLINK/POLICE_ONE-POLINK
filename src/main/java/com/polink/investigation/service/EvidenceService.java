package com.polink.investigation.service;

import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.repository.CaseExistenceRepository;
import com.polink.investigation.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    // 임시 사건 번호 조회용 Repository로 이후에 삭제 예정
    private final CaseExistenceRepository caseExistenceRepository;

    public EvidenceService(EvidenceRepository evidenceRepository, CaseExistenceRepository caseExistenceRepository) {
        this.evidenceRepository = evidenceRepository;
        this.caseExistenceRepository = caseExistenceRepository;
    }

    // 증거물 등록
    @Transactional
    public EvidenceCreateResponse createEvidence(
            String caseId,
            EvidenceCreateRequest request
    ) {
        // 1. 증거물을 등록할 사건이 존재하는지 확인
        checkCaseExists(caseId);

        // 2. 해당 사건에 저장된 증거물 목록 조회
        List<Evidence> savedEvidences = evidenceRepository.findByCaseId(caseId);

        // 3. 다음 증거번호 생성
        String evidenceId = generateEvidenceId(caseId, savedEvidences);

        // 4. 요청 정보로 새 증거물 객체 생성
        Evidence evidence = new Evidence(
                evidenceId,
                caseId,
                request.evidenceName(),
                request.evidenceType(),
                request.description(),
                LocalDateTime.now(),
                request.acquiredLocation()
        );

        // 5. 증거물 저장
        Evidence savedEvidence = evidenceRepository.save(evidence);

        // 6. 변환 메서드를 호출하여 응답 DTO로 변환
        return EvidenceCreateResponse.from(savedEvidence);
    }

    // 1-1. 접수된 사건이 없으면 증거물 등록 불가
    private void checkCaseExists(String caseId) {

        if (!caseExistenceRepository.existsByCaseId(caseId)) {
            throw new IllegalArgumentException(
                    "해당하는 사건을 찾을 수 없어 증거물을 등록할 수 없습니다."
            );
        }
    }

    // 3-1. 같은 사건에 저장된 증거물의 마지막 순번 다음 번호를 생성
    private String generateEvidenceId(
            String caseId,
            List<Evidence> savedEvidences
    ) {
        // (1) 사건번호의 인덱스 5부터 끝까지 가져와 앞의 "SMPA-"를 제외한다.
        // (2) Evidence의 약자인 'EV-'에 가져온 문자열과 '-' 를 붙인다.
        // ex) SMPA-2026-0405 → EV-2026-0405-
        String newPrefix = "EV-" + caseId.substring(5) + "-";

        int lastNumber = 0;

        // 저장된 증거물이 없으면 반복하지 않고 0을 유지
        for (Evidence evidence : savedEvidences) {
            String evidenceId = evidence.getEvidenceId();

            // 증거 번호의 마지막 하이픈 뒤에 있는 순번 추출
            String numberText = evidenceId.substring(
                    evidenceId.lastIndexOf("-") + 1
            );

            // 문자열 순번을 숫자로 변환
            int number = Integer.parseInt(numberText);

            // 저장된 순번 중 가장 큰 값 유지
            lastNumber = Math.max(lastNumber, number);
        }
        // 두 자리 순번의 최대값을 넘으면 등록 중단
        if (lastNumber >= 99) {
            throw new IllegalStateException(
                    "해당 사건의 증거번호는 99번까지 등록할 수 있습니다."
            );
        }

        /* 마지막 순번에 1을 더해 다음 순번 생성 ex) EV-2026-0405-04
         *  %02d: 순번을 두 자리로 표시(1 → 01)
         *  Locale.ROOT: 언어 설정과 관계없이 0~9 사용
         * */
        return newPrefix
                + String.format(Locale.ROOT, "%02d", lastNumber + 1);
    }
}