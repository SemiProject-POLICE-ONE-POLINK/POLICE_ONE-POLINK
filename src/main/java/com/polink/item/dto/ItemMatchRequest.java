package com.polink.item.dto;

import jakarta.validation.constraints.NotBlank;

/*
 * AI 물품 매칭 요청 DTO다.
 * 시민이 본인이 등록한 분실물 번호를 전달하면,
 * 해당 분실물과 매칭 가능한 습득물 후보를 조회한다.
 */
public record ItemMatchRequest(

        // 분실물 번호는 L-0001 형식이며, 비어 있으면 후보 매칭을 할 수 없다.
        @NotBlank(message = "분실물 번호는 필수입니다.")
        String lostItemId
) {
}