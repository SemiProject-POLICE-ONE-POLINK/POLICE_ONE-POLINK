package com.polink.item.returnrequest.dto;

import java.util.List;

/**
 * 로그인한 시민이 신청한 반환 신청 목록 응답값이다.
 */
public record MyReturnRequestListResponse(
        List<ReturnRequestListResponse> items
) {
}
