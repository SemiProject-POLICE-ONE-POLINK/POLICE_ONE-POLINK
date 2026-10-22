package com.polink.item.dto;

import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/*
 * 경찰·관리자가 분실물과 습득물 현황을 기간별로 확인할 때 사용하는 응답 DTO다.
 */
public record ItemStatisticsResponse(

        // 조회에 적용된 등록일 시작일이다. 값이 없으면 전체 기간을 뜻한다.
        LocalDate startDate,

        // 조회에 적용된 등록일 종료일이다. 값이 없으면 전체 기간을 뜻한다.
        LocalDate endDate,

        // 조건에 맞는 분실물·습득물 전체 등록 건수다.
        long totalCount,

        // 분실물과 습득물의 개수를 바로 표시하기 위한 요약 값이다.
        long lostCount,
        long foundCount,

        // 반환 절차 진행 현황을 바로 표시하기 위한 요약 값이다.
        long handoverPendingCount,
        long returnedCount,

        // LOST / FOUND 유형별 물품 개수다.
        Map<ItemType, Long> itemTypeCounts,

        // LOST / STORED / HANDOVER_PENDING / RETURNED 상태별 물품 개수다.
        Map<ItemStatus, Long> statusCounts,

        // 카테고리별 물품 개수다.
        List<ItemCategoryCountResponse> categoryCounts,

        // 등록일 기준 일별 물품 등록 건수다.
        List<ItemDailyCountResponse> dailyCounts,

        // 등록일 기준 월별 물품 등록 건수다.
        List<ItemMonthlyCountResponse> monthlyCounts
) {
}