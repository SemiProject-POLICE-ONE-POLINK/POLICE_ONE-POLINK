package com.polink.item.dto;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.time.LocalDate;

/** 목록 화면에 표시할 가벼운 물품 정보다. */
public record ItemListResponse(
        String itemId,
        ItemType itemType,
        String itemName,
        String category,
        String location,
        LocalDate occurredAt,
        ItemStatus status,
        String imagePath
) {
    public static ItemListResponse from(Item item) {
        return new ItemListResponse(
                item.getItemId(),
                item.getItemType(),
                item.getItemName(),
                item.getCategory(),
                item.getLocation(),
                item.getOccurredAt().toLocalDate(),
                item.getStatus(),
                item.getImagePath()
        );
    }
}
