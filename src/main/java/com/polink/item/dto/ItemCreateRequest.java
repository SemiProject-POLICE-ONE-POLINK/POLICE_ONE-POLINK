package com.polink.item.dto;

import com.polink.item.entity.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/*
 * 분실물·습득물 등록 화면에서 서버로 보내는 요청값이다.
 * 등록자 ID는 로그인 정보에서 서버가 결정하므로 요청값에 포함하지 않는다.
 */
public record ItemCreateRequest(

        @NotNull(message = "물품 유형은 필수입니다.")
        ItemType itemType,

        @NotBlank(message = "물품명은 필수입니다.")
        String itemName,

        @NotBlank(message = "카테고리는 필수입니다.")
        String category,

        @NotBlank(message = "물품 설명은 필수입니다.")
        String description,

        @NotBlank(message = "발생 장소는 필수입니다.")
        String location,

        @NotNull(message = "발생 일자는 필수입니다.")
        @PastOrPresent(message = "발생 일자는 오늘 날짜보다 미래일 수 없습니다.")
        LocalDate occurredAt
) {
}
