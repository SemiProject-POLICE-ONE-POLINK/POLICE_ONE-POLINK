package com.polink.casefile.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CaseCloseRequest {

    @NotBlank(message = "사건 종결 사유는 필수입니다.")
    private String closeReason;
}
