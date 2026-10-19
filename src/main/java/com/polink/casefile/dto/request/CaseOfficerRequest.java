package com.polink.casefile.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CaseOfficerRequest {

    @NotNull(message = "담당 경찰 회원 번호는 필수입니다.")
    @Positive(message = "회원 번호는 양수여야 합니다.")
    private Integer officerId;
}