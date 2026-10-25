package com.polink.item.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 아직 등록하지 않은 분실물 설명으로 AI 일치도를 미리 확인할 때 쓰는 요청값이다.
 * 이 요청은 tbl_item에 물품을 저장하거나 반환 신청을 만들지 않는다.
 */
public record ItemMatchPreviewRequest(
        @NotBlank(message = "물품명은 필수입니다.")
        String itemName,

        @NotBlank(message = "물품 종류는 필수입니다.")
        String category,

        String description,
        String location,
        LocalDate occurredAt
) {
}
