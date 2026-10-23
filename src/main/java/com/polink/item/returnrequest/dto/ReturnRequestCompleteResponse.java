package com.polink.item.returnrequest.dto;

import java.time.LocalDateTime;

/*
 * 실제 물품 인계가 끝났을 때 반환하는 응답이다.
 * LocalDateTime은 JSON에서 ISO 날짜·시간 형식으로 변환된다.
 */
public record ReturnRequestCompleteResponse(
        // 완료된 반환 신청 번호다.
        Integer requestId,

        /*
         * API 명세의 returnedAt 필드명과 맞춘 실제 물품 인계 완료 시각이다.
         * ReturnRequest 엔티티 내부 컬럼명(completedAt)과 달라도,
         * 외부 API 계약은 반환 물품의 완료 시각이라는 의미를 우선한다.
         */
        LocalDateTime returnedAt
) {
}
