package com.polink.item.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * 분실물 또는 습득물 수정 요청값이다.
 * 물품 유형, 등록자, 상태는 수정할 수 없고 기본 정보만 수정한다.
 */
public record ItemUpdateRequest(

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
