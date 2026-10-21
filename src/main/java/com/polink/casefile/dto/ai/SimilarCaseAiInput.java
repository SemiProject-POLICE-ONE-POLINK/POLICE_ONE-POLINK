package com.polink.casefile.dto.ai;

import com.polink.casefile.entity.CaseType;

import java.time.LocalDateTime;
import java.util.List;

public record SimilarCaseAiInput(
        String situation,
        List<CaseCandidate> candidates
) {
    public record CaseCandidate(
            String caseId,
            String title,
            CaseType caseType,
            String description,
            LocalDateTime occurredAt,
            String location
    ) {
    }
}
