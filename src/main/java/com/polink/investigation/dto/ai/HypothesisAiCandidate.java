package com.polink.investigation.dto.ai;

import java.util.List;

// OpenAI가 생성한 가설 후보의 원본 데이터
// 신뢰도 점수는 AI에게 받지 않고, 검증 후 서버에서 계산
public record HypothesisAiCandidate(
        String content,                       // 가설 내용
        String reason,                        // 판단 근거 설명
        String additionalRecommendation,      // 추가 확인 사항
        List<String> evidenceIds,             // 가설을 지지하는 증거 번호
        List<Integer> statementIds,           // 가설을 지지하는 진술 번호
        List<String> opposingEvidenceIds,     // 가설과 반대되는 증거 번호
        List<Integer> opposingStatementIds,   // 가설과 반대되는 진술 번호
        List<Corroboration> corroborations,    // 증거물·진술이 같은 사실을 확인한 경우
        List<String> circumstances             // 불일치 등 추가 정황
) {
    // 서로 같은 사실을 확인해 주는 증거물과 진술의 연결
    //
    public record Corroboration(
            String evidenceId,
            Integer statementId,
            String sharedFact        // 두 자료가 공통으로 확인한 사실 (예: 현장 주변의 흰색 경차)
    ) {
    }
}
