package com.polink.item.returnrequest.dto;

import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;

/*
 * 반환 신청 목록에서 한 건을 표현하는 응답값이다.
 */
public record ReturnRequestListResponse(
        Integer requestId,
        ReturnRequestStatus status
) {

    public static ReturnRequestListResponse from(ReturnRequest returnRequest) {
        return new ReturnRequestListResponse(
                returnRequest.getRequestId(),
                returnRequest.getStatus()
        );
    }
}
