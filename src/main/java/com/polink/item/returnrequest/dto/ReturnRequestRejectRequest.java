package com.polink.item.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;

/*
 * 경찰관이 반환 신청을 반려할 때 입력하는 사유다.
 */
public record ReturnRequestRejectRequest(

        @NotBlank(message = "반려 사유는 필수입니다.")
        String rejectReason
) {

}
