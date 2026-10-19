package com.polink.investigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDateTime;

// 수사 기록 요청 DTO
public record InvestigationLogCreateRequest(
        @NotBlank(message = "수사 내용을 입력해주세요.")
        String content,

        @NotNull(message = "수사 일시를 입력해주세요.")
        @PastOrPresent(message = "수사 일시는 미래로 설정할 수 없습니다.")
        LocalDateTime investigatedAt
) {
}
