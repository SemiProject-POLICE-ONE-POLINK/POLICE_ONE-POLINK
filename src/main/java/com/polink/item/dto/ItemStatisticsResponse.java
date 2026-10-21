package com.polink.item.dto;

import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.util.List;
import java.util.Map;

/**
 * 경찰용 분실물·습득물 현황 통계 응답 DTO다.
 */
public record ItemStatisticsResponse(
        long totalCount,

        // LOST / FOUND 유형별 물품 개수
        Map<ItemType, Long> itemTypeCounts,

        // LOST / STORED / HANDOVER_PENDING / RETURNED 상태별 물품 개수
        Map<ItemStatus, Long> statusCounts,

        // 카테고리별 물품 개수
        List<ItemCategoryCountResponse> categoryCounts
) {
}