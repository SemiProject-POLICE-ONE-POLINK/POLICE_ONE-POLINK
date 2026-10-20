package com.polink.investigation.dto.response;


import java.util.List;

// AI가 생성한 가설 후보 1개의 응답 DTO
// 아직 DB에 저장되지 않았으므로 hypothesisId는 포함하지 않음
public record HypothesisCandidateResponse(

        String content,                       // AI가 제안한 가설 내용
        String reason,                        // AI가 제시한 판단 근거
        String additionalRecommendation,      // 추가로 확인할 수사 사항
        Integer confidence,                   // 신뢰도(0~100)
        List<String> evidenceIds,              // 근거로 사용한 증거물 번호
        List<Integer> statementIds             // 근거로 사용한 진술 번호
) {
}
