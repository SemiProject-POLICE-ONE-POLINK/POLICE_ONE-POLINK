package com.polink.item.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;

/*
 * 시민이 반환 신청을 등록할 때 보내는 요청값이다.
 */
public record ReturnRequestCreateRequest(

        // 본인이 등록한 분실물 번호. 예: L-0001
        @NotBlank(message = "분실물 번호는 필수입니다.")
        String lostItemId,

        // 반환받고 싶은 습득물 번호. 예: F-0001
        @NotBlank(message = "습득물 번호는 필수입니다.")
        String foundItemId,

        // 반환 신청 이유
        @NotBlank(message = "반환 신청 사유는 필수입니다.")
        String requestReason
) {

}
