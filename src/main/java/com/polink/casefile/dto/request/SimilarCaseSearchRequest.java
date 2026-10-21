package com.polink.casefile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SimilarCaseSearchRequest {

    @NotBlank(message = "상황 설명은 필수입니다.")
    @Size(max = 1000, message = "상황 설명은 1000자 이하여야 합니다.")
    private String situation;
}
