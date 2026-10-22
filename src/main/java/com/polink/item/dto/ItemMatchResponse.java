package com.polink.item.dto;

import com.polink.item.entity.ItemStatus;
import java.time.LocalDate;

/*
 * 분실물과 유사하다고 판단된 습득물 후보 한 건의 응답 DTO다.
 * 후보 습득물의 기본 정보, 이미지 경로, 매칭 근거와 점수를 함께 반환한다.
 */
public record ItemMatchResponse(

        // 매칭 후보 습득물의 번호다. 예: F-0211
        String foundItemId,

        // 후보 습득물의 이름과 분류 정보다.
        String foundItemName,
        String foundItemCategory,

        // 사용자가 후보를 비교할 수 있도록 기본 설명·발견 장소·날짜를 제공한다.
        String foundItemDescription,
        String foundItemLocation,
        LocalDate foundOccurredAt,

        // 반환 가능 여부를 확인할 수 있는 현재 습득물 상태다.
        ItemStatus foundItemStatus,

        // 업로드된 이미지가 없다면 null이다.
        String foundImagePath,

        // AI 또는 규칙 기반 매칭이 판단한 유사 이유다.
        String reason,

        // 분실물과 습득물의 유사도 점수다. 범위는 0~100이다.
        int score
) {
}