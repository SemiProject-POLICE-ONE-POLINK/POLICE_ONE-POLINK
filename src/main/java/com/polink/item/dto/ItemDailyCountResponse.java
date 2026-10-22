package com.polink.item.dto;

import java.time.LocalDate;

/*
 * 특정 날짜에 등록된 물품 건수를 전달하는 통계 DTO다.
 * createdAt 등록 시각을 날짜 단위로 묶어 일별 추이를 보여 줄 때 사용한다.
 */
public record ItemDailyCountResponse(

        // 물품이 등록된 날짜다.
        LocalDate date,

        // 해당 날짜에 등록된 분실물·습득물의 전체 건수다.
        long count
) {
}