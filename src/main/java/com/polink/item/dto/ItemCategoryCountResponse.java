package com.polink.item.dto;

/**
 * 카테고리별 물품 개수를 전달하는 응답 DTO다.
 * 예: ELECTRONICS 카테고리 물품이 5개면 category=ELECTRONICS, count=5
 */
public record ItemCategoryCountResponse(
        String category,
        long count
) {
}
