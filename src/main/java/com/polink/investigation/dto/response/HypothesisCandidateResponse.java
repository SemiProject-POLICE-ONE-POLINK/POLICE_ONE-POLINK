package com.polink.investigation.dto.response;


import java.util.List;

// AI가 생성한 가설 후보 1개의 응답 DTO
// 아직 DB에 저장되지 않았으므로 hypothesisId는 포함하지 않음
public record HypothesisCandidateResponse(
        String candidateId,                    // 채택할 AI 후보를 찾기 위한 임시 번호
        String content,                        // AI가 제안한 가설 내용
        String reason,                         // AI가 제시한 판단 근거
        String additionalRecommendation,       // 추가로 확인할 수사 사항
        Integer confidence,                    // 신뢰도(0~100)
        List<String> evidenceIds,              // 근거로 사용한 증거물 번호
        List<Integer> statementIds,             // 근거로 사용한 진술 번호
        List<Integer> logIds,                   // 근거로 사용한 수사 기록 번호
        List<String> opposingEvidenceIds,       // 반대 증거물 번호
        List<Integer> opposingStatementIds,     // 반대 진술 번호
        List<String> circumstances             // AI가 사건 자료를 바탕으로 해석한 정황
) {
    
    // 화면에는 null 대신 빈 배열 반환
    public HypothesisCandidateResponse {
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds;
        statementIds = statementIds == null ? List.of() : statementIds;
        logIds = logIds == null ? List.of() : logIds;
        opposingEvidenceIds =
                opposingEvidenceIds == null ? List.of() : opposingEvidenceIds;
        opposingStatementIds =
                opposingStatementIds == null ? List.of() : opposingStatementIds;
        circumstances =
                circumstances == null ? List.of() : circumstances;
    }
}