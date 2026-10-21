package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseType;
import lombok.Getter;

import java.util.List;

@Getter
public class SimilarCaseSearchResponse {

    private final String analysis;
    private final CaseType suggestedCaseType;
    private final String recommendedAction;
    private final List<SimilarCaseResponse> similarCases;

    public SimilarCaseSearchResponse(
            String analysis,
            CaseType suggestedCaseType,
            String recommendedAction,
            List<SimilarCaseResponse> similarCases
    ) {
        this.analysis = analysis;
        this.suggestedCaseType = suggestedCaseType;
        this.recommendedAction = recommendedAction;
        this.similarCases = similarCases;
    }
}
