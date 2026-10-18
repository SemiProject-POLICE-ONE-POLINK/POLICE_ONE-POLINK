package com.polink.item.dto;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.time.LocalDateTime;

/** 등록이 성공한 뒤 클라이언트에 반환하는 최소 물품 정보다. */
public record ItemCreateResponse(
        String itemId,
        ItemType itemType,
        String itemName,
        ItemStatus status,
        LocalDateTime createdAt
) {
    // Entity 전체를 그대로 반환하지 않고, 화면에 필요한 등록 결과만 골라서 반환한다.
    public static ItemCreateResponse from(Item item) {
        return new ItemCreateResponse(
                item.getItemId(),
                item.getItemType(),
                item.getItemName(),
                item.getStatus(),
                item.getCreatedAt()
        );
    }
}
