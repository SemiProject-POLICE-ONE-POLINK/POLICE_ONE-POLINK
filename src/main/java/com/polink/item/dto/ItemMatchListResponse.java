package com.polink.item.dto;

import java.util.List;

/*
 * AI 물품 매칭 API의 최종 응답 DTO다.
 * matches가 비어 있으면 현재 조건에 맞는 습득물 후보가 없다는 뜻이다.
 */
public record ItemMatchListResponse(

        // 유사도 기준으로 정렬된 습득물 후보 목록이다.
        List<ItemMatchResponse> matches
) {
}