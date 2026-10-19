package com.polink.item.returnrequest.dto;

import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;

/*
 * 반환 신청 등록 성공 후 반환하는 최소 정보다.
 */
public record ReturnRequestCreateResponse(
        Integer requestId,
        ReturnRequestStatus status
) {

    public static ReturnRequestCreateResponse from(ReturnRequest returnRequest) {
        return new ReturnRequestCreateResponse(
                returnRequest.getRequestId(),
                returnRequest.getStatus()
        );
    }
}
