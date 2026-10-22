package com.polink.item.dto;

/*
 * 특정 연도·월에 등록된 물품 건수를 전달하는 통계 DTO다.
 * 프론트엔드는 year와 month를 조합해 월별 차트의 라벨로 사용할 수 있다.
 */
public record ItemMonthlyCountResponse(

        // 통계 대상 연도다. 예: 2026
        int year,

        // 통계 대상 월이다. 1부터 12까지의 값이다.
        int month,

        // 해당 연월에 등록된 분실물·습득물의 전체 건수다.
        long count
) {
}