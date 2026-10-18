package com.polink.investigation.dto.request;

import com.polink.investigation.entity.StatementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatementCreateRequest(

        // 진술 유형 — 추가·정정·번복 중 선택
        @NotNull(message = "진술 유형을 선택해주세요.")
        StatementType statementType,

        // 새로 등록할 진술 내용
        @NotBlank(message = "진술 내용을 입력해주세요.")
        String content
) {
}
