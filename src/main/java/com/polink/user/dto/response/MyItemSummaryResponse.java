package com.polink.user.dto.response;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;

import java.time.LocalDate;

// 시민 마이페이지 분실물·습득물(요약)
public record MyItemSummaryResponse(
        String itemId,                    // 물품 번호
        String itemName,                  // 물품명
        ItemType itemType,                // 분실물·습득물 구분
        ItemStatus itemStatus,            // 분실 중·보관 중 등 물품 상태
        String location,                  // 분실·습득 장소
        LocalDate occurredAt,             // 분실·습득 일자
        Integer returnRequestId,          // 반환 번호 - 반환 신청이 없을 시 null
        ReturnRequestStatus returnStatus  // 반환 상태 - 반환 신청이 없을 시 null
) {
    // 물품과 연결된 반환 신청을 카드 표시 정보로 변환
    public MyItemSummaryResponse(Item item, ReturnRequest returnRequest) {
        this(
                item.getItemId(),
                item.getItemName(),
                item.getItemType(),
                item.getStatus(),
                item.getLocation(),
                item.getOccurredAt().toLocalDate(),
                returnRequest == null ? null : returnRequest.getRequestId(),
                returnRequest == null ? null : returnRequest.getStatus()
        );
    }
}
