package com.polink.casefile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CaseCancelRequest {

    @NotBlank(message = "사건 취소 사유는 필수입니다.")
    @Size(
            max = 500,
            message = "사건 취소 사유는 500자 이하여야 합니다."
    )
    private String cancelReason;
}
