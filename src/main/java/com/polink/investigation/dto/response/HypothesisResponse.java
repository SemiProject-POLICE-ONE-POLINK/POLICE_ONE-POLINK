package com.polink.investigation.dto.response;


import com.polink.investigation.dto.HypothesisBasis;

import java.time.LocalDateTime;

// 채택되어 저장된 수사 가설 응답 DTO
public record HypothesisResponse(

        Integer hypothesisId,                // 저장된 가설 번호
        String caseId,                       // 가설이 속한 사건 번호
        String content,                      // 가설 내용
        String reason,                       // AI 분석 근거
        String additionalRecommendation,     // 추가 확인 사항
        Integer confidence,                  // AI 신뢰도(0~100)
        String source,                       // 가설 출처: 현재는 AI
        HypothesisBasis basis,               // 가설의 근거가 된 증거물 번호와 진술 번호를 묶은 자료형
        Integer adoptedByUserId,             // 채택한 경찰의 회원 번호
        String adoptedByName,                // 채택한 경찰의 이름
        LocalDateTime adoptedAt              // 채택 일시
) {
}