package com.polink.item.dto;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** 상세 화면에서 보여줄 물품의 전체 정보다. */
public record ItemDetailResponse(
        String itemId,
        Integer registrantId,
        ItemType itemType,
        String itemName,
        String category,
        String description,
        String location,
        LocalDate occurredAt,
        ItemStatus status,
        LocalDateTime createdAt,
        LocalDateTime returnedAt,
        String imagePath
) {
    public static ItemDetailResponse from(Item item) {
        return new ItemDetailResponse(
                item.getItemId(),
                item.getRegistrantId(),
                item.getItemType(),
                item.getItemName(),
                item.getCategory(),
                item.getDescription(),
                item.getLocation(),
                item.getOccurredAt().toLocalDate(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getReturnedAt(),
                item.getImagePath()
        );
    }
}
