package com.polink.item.dto.ai;

/*
 * AI 또는 규칙 기반 매칭이 반환하는 후보 한 건이다.
 * 외부 AI가 반환한 값은 Service에서 한 번 더 검증한 뒤
 * 기존 API 응답 DTO로 변환할 예정이다.
 */
public record ItemMatchAiCandidate(

        // 매칭 후보로 선택된 습득물 번호다.
        String foundItemId,

        // 분실물과 습득물이 유사하다고 판단한 설명이다.
        // 소유권 확정 문구가 아닌 참고용 안내 문구만 허용한다.
        String reason,

        /*
         * 분실물과 습득물의 유사도를 0~100점 범위로 나타낸다.
         * Service는 이 점수로 최소 기준 미달 후보를 제외하고,
         * 프론트엔드는 높은 점수 순으로 후보를 보여줄 수 있다.
         */
        int score
) {
}