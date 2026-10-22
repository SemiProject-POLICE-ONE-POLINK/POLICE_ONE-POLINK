package com.polink.user.dto.response;

import java.util.List;

// 시민 마이페이지의 사건·물품 목록
public record CitizenMyPageResponse(
        long totalCaseCount,                // 내가 접수한 사건 전체 건수
        List<MyCaseSummaryResponse> cases,  // 나의 전체 사건(최신 등록순)

        long totalItemCount,                // 내가 등록한 물품 전체 건수
        List<MyItemSummaryResponse> items   // 나의 전체 분실물·습득물, 최신 등록순
) {
}
