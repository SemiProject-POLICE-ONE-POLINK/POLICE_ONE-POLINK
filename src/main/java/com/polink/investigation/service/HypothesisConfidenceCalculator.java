package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.springframework.stereotype.Component;

// AI가 제시한 근거의 수를 바탕으로 화면에 표시할 자료 신뢰도(부합도) 점수를 계산
@Component
public class HypothesisConfidenceCalculator {

    public int calculate(HypothesisAiCandidate candidate) {
        int score = 20; // 기본점

        // 지지 증거물은 최대 2건까지 가산
        score += Math.min(candidate.evidenceIds().size(), 2) * 15;

        // 지지 진술은 최대 2건까지 가산
        score += Math.min(candidate.statementIds().size(), 2) * 10;

        // 증거물과 진술이 같은 사실을 확인했다면 10점 가산
        if (!candidate.corroborations().isEmpty()) {
            score += 10;
        }

        // 가설과 맞지 않는 자료(증거물, 진술)은/는 건당 15점 감점
        int opposingCount = candidate.opposingEvidenceIds().size()
                + candidate.opposingStatementIds().size();
        score -= opposingCount * 15;

        // 화면에 표시할 점수는 5~95 사이로 제한
        return Math.max(5, Math.min(score, 95));
    }
}
