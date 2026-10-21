package com.polink.investigation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InvestigationCloseRequest(

        @NotBlank(message = "수사 종결 사유를 입력해주세요.")
        String closeReason      // 수사 종결 사유
) {
}
