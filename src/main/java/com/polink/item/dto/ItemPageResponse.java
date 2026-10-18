package com.polink.item.dto;

import java.util.List;

/**
 * 목록 데이터와 페이지 정보를 한 번에 전달하는 응답 DTO다.
 * page는 0부터 시작한다.
 * 예: page=0, size=10은 첫 번째 페이지에서 최대 10개를 조회한다.
 */
public record ItemPageResponse(
        List<ItemListResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}