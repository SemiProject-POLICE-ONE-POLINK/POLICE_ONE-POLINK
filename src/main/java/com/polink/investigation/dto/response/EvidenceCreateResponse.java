package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;

import java.time.LocalDateTime;

public record EvidenceCreateResponse(

        String evidenceId,
        String caseId,
        String evidenceName,
        EvidenceType evidenceType,
        String description,
        LocalDateTime acquiredAt,
        String acquiredLocation,
        boolean fileAttached
) {
    // 저장된 증거물 Entity를 응답 DTO로 변환하는 메서드
    public static EvidenceCreateResponse from(Evidence evidence) {
        return new EvidenceCreateResponse(
                evidence.getEvidenceId(),
                evidence.getCaseId(),
                evidence.getEvidenceName(),
                evidence.getEvidenceType(),
                evidence.getDescription(),
                evidence.getAcquiredAt(),
                evidence.getAcquiredLocation(),
                evidence.getFilePath() != null
        );
    }
}
