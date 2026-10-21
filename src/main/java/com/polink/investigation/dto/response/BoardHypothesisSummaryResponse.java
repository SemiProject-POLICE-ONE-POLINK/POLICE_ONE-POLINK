package com.polink.investigation.dto.response;

import com.polink.investigation.entity.StatementType;

import java.time.LocalDateTime;
import java.util.List;

// 수사보드에 표시할 가장 최근 채택 가설 1건
public record BoardHypothesisSummaryResponse(
        Integer hypothesisId,               // 저장된 가설 번호
        String content,                     // 가설 내용
        String reason,                      // AI가 제시한 판단 근거
        Integer confidence,                 // 신뢰도
        String source,                      // 가설 출처: 현재는 AI
        String adoptedByName,               // 채택한 경찰 이름
        LocalDateTime adoptedAt,            // 채택 일시
        List<EvidenceBasis> evidences,      // 근거로 사용한 모든 증거물
        List<StatementBasis> statements,    // 근거로 사용한 모든 진술
        List<String> circumstances          // AI가 자료를 바탕으로 해석한 정황 문장
) {
    // 근거 증거물의 화면 표시 정보
    public record EvidenceBasis(
            String evidenceId,
            String name,
            String description
    ) {
    }

    // 근거 진술의 화면 표시 정보
    public record StatementBasis(
            Integer statementId,
            String witnessName,
            StatementType statementType,
            String content
    ) {
    }
}