package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseType;
import lombok.Getter;

@Getter
public class SimilarCaseResponse {

    private final String caseId;
    private final String title;
    private final CaseType caseType;
    private final String similarityReason;

    public SimilarCaseResponse(
            String caseId,
            String title,
            CaseType caseType,
            String similarityReason
    ) {
        this.caseId = caseId;
        this.title = title;
        this.caseType = caseType;
        this.similarityReason = similarityReason;
    }
}