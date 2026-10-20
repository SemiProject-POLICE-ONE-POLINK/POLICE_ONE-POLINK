package com.polink.item.returnrequest.dto;

import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;

/**
 * 경찰관이 반환 신청 한 건을 상세 조회할 때 사용하는 응답값이다.
 */
public record ReturnRequestDetailResponse(
        Integer requestId,
        String lostItemId,
        String foundItemId,
        String requestReason,
        ReturnRequestStatus status
) {

    public static ReturnRequestDetailResponse from(
            ReturnRequest returnRequest
    ) {
        return new ReturnRequestDetailResponse(
                returnRequest.getRequestId(),
                returnRequest.getLostItemId(),
                returnRequest.getFoundItemId(),
                returnRequest.getRequestReason(),
                returnRequest.getStatus()
        );
    }
}
