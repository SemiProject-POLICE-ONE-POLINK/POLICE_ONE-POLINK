package com.polink.item.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

/*
 * AI 매칭 요청에 필요한 최소 물품 정보만 담는 DTO다.
 * 사용자 ID, 연락처 같은 개인정보는 AI에 보내지 않는다.
 * 서버가 검증한 분실물과 STORED 상태의 습득물 후보만 전달한다.
 */
public record ItemMatchAiInput(

        // 매칭 기준이 되는 분실물 한 건이다.
        ItemInput lostItem,

        // 서버가 미리 조회·제한한 습득물 후보 목록이다.
        List<ItemInput> foundCandidates
) {

    /*
     * AI가 물품 특징을 비교하는 데 필요한 최소 정보다.
     * Entity를 그대로 외부 API에 전달하지 않기 위해 별도 DTO로 둔다.
     */
    public record ItemInput(

            // 후보 검증과 결과 연결에만 쓰는 물품 번호다.
            String itemId,

            // LOST 또는 FOUND 유형이다.
            String itemType,

            // 물품 이름과 카테고리다.
            String itemName,
            String category,

            // 등록자가 작성한 물품 특징 설명이다.
            String description,

            // 분실·습득 장소와 발생 시각이다.
            String location,
            LocalDateTime occurredAt
    ) {
    }
}