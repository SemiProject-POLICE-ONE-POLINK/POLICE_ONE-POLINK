package com.polink.item.returnrequest.dto;

import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import java.time.LocalDateTime;

/*
 * 반환 신청을 승인하거나 반려한 뒤 클라이언트에 돌려주는 응답 DTO다.
 * 기존에는 204 No Content만 반환해서,
 * 프론트엔드가 실제 처리된 신청 번호·처리 결과·처리 시각을 알 수 없었다.
 * 명세에 맞춰 아래 세 정보를 200 OK 본문으로 반환한다.
 */
public record ReturnRequestProcessResponse(

        // 실제 처리된 반환 신청 번호다.
        Integer requestId,

        // 승인(APPROVED) 또는 반려(REJECTED) 처리 후의 상태다.
        ReturnRequestStatus status,

        // 경찰관이 승인 또는 반려를 처리한 서버 시각이다.
        LocalDateTime processedAt
) {
    /*
     * Entity를 API 응답 DTO로 변환한다.
     * 승인·반려 로직이 Entity의 status와 processedAt을 먼저 변경한 뒤
     * 이 메서드를 호출해야 변경된 값이 응답에 포함된다.
     */
    public static ReturnRequestProcessResponse from(
            ReturnRequest returnRequest
    ) {
        return new ReturnRequestProcessResponse(
                returnRequest.getRequestId(),
                returnRequest.getStatus(),
                returnRequest.getProcessedAt()
        );
    }
}