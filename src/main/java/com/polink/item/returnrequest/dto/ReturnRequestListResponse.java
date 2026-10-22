package com.polink.item.returnrequest.dto;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import java.time.LocalDateTime;

/*
 * 반환 신청 목록 한 건을 화면에 전달하는 응답 DTO다.
 * 경찰은 전체 신청 목록을, 시민은 본인 신청 목록을 조회한다.
 * 두 화면 모두 공통으로 사용할 수 있도록 신청자·물품·처리 상태 정보를 담는다.
 * User Entity와 JPA 연관관계를 직접 맺지 않고,
 * 현재 도메인 경계를 유지하기 위해 신청자 이름 대신 requesterId만 반환한다.
 */
public record ReturnRequestListResponse(

        // 반환 신청 DB 번호다.
        Integer requestId,

        // 반환 신청을 만든 시민의 회원 번호다.
        Integer requesterId,

        // 연결된 분실물 번호다. 예: L-0116
        String lostItemId,

        // 목록에서 분실물을 구분할 수 있도록 분실물 이름을 함께 반환한다.
        String lostItemName,

        // 분실물의 현재 상태다. LOST, HANDOVER_PENDING, RETURNED 등이 올 수 있다.
        ItemStatus lostItemStatus,

        // 연결된 습득물 번호다. 예: F-0216
        String foundItemId,

        // 목록에서 습득물을 구분할 수 있도록 습득물 이름을 함께 반환한다.
        String foundItemName,

        // 습득물의 현재 상태다. STORED, HANDOVER_PENDING, RETURNED 등이 올 수 있다.
        ItemStatus foundItemStatus,

        // 반환 신청 처리 상태다. PENDING, APPROVED, REJECTED, COMPLETED 등이 올 수 있다.
        ReturnRequestStatus status,

        // 시민이 반환 신청을 만든 서버 시각이다.
        LocalDateTime createdAt,

        // 경찰이 승인 또는 반려를 처리한 서버 시각이다.
        // 아직 PENDING 상태면 null이다.
        LocalDateTime processedAt
) {

    /*
     * ReturnRequest Entity와 실제 분실물·습득물 Entity를
     * 목록 화면 전용 DTO 한 건으로 변환한다.
     * ReturnRequest에는 물품 번호만 저장되어 있으므로,
     * 물품 이름과 상태를 반환하려면 Service에서 Item을 조회해 이 메서드로 전달해야 한다.
     */
    public static ReturnRequestListResponse from(
            ReturnRequest returnRequest,
            Item lostItem,
            Item foundItem
    ) {
        return new ReturnRequestListResponse(
                // 반환 신청 기본 정보
                returnRequest.getRequestId(),
                returnRequest.getRequesterId(),

                // 연결된 분실물 정보
                lostItem.getItemId(),
                lostItem.getItemName(),
                lostItem.getStatus(),

                // 연결된 습득물 정보
                foundItem.getItemId(),
                foundItem.getItemName(),
                foundItem.getStatus(),

                // 신청 처리 상태와 시간 정보
                returnRequest.getStatus(),
                returnRequest.getCreatedAt(),
                returnRequest.getProcessedAt()
        );
    }
}