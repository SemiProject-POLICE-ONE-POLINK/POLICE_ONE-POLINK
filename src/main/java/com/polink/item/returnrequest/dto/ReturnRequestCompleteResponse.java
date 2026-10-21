package com.polink.item.returnrequest.dto;

import java.time.LocalDateTime;

/*
 * 실제 물품 인계가 끝났을 때 반환하는 응답이다.
 * LocalDateTime은 JSON에서 ISO 날짜·시간 형식으로 변환된다.
 */
public record ReturnRequestCompleteResponse(
        // 완료된 반환 신청 번호다.
        Integer requestId,

        // 실제 반환 완료가 기록된 서버 시각이다.
        LocalDateTime returnedAt
) {
}