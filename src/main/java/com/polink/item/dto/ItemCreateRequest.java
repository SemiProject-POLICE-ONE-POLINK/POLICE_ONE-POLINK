package com.polink.item.dto;

import com.polink.item.entity.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/*
 * 분실물·습득물 등록 화면에서 서버로 보내는 요청값이다.
 * 물품 유형은 URL(/lost, /found)로 결정하므로 요청값에 넣지 않는다.
 */
public record ItemCreateRequest(
        /*
         * LOST는 분실물, FOUND는 습득물이다.
         * 하나의 POST /api/items 주소에서 두 등록 유형을 구분하기 위해 사용한다.
         */
        @NotNull(message = "물품 유형은 필수입니다.")
        ItemType itemType,

        /* 로그인 기능 구현 전, 테스트에 사용하는 등록자 회원 번호 */
        @NotNull(message = "등록자 회원 번호는 필수입니다.")
        @Positive(message = "등록자 회원 번호는 1 이상이어야 합니다.")
        Integer registrantId,

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
    // record는 요청값을 담기 위한 간단한 DTO다.
    // Lombok 없이도 생성자와 getter 역할의 registrantId(), itemName() 등을 자동으로 제공한다.
}
