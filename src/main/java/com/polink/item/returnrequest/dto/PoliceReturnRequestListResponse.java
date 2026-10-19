package com.polink.item.returnrequest.dto;

import java.util.List;

/**
 * 경찰관이 조회하는 반환 신청 목록 응답값이다.
 */
public record PoliceReturnRequestListResponse(
        List<ReturnRequestListResponse> items
) {
}
