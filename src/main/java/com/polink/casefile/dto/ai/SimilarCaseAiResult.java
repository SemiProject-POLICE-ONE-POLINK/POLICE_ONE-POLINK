package com.polink.casefile.dto.ai;

import com.polink.casefile.entity.CaseType;

import java.util.List;

public record SimilarCaseAiResult(
        String analysis,
        CaseType suggestedCaseType,
        String recommendedAction,
        List<Match> matches
) {
    public record Match(
            String caseId,
            String similarityReason
    ) {
    }
}
