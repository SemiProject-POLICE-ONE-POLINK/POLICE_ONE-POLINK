package com.polink.investigation.dto.response;

import java.util.List;

// 한 사건에 대해 AI가 생성한 가설 후보 목록 응답
public record HypothesisGenerateResponse(

        String caseId,                                              // 추론 대상 사건 번호
        List<HypothesisCandidateResponse> hypotheses                // AI가 생성한 가설 후보(3개)
) {
}
