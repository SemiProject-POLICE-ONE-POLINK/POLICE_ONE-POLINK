package com.polink.investigation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WitnessCreateRequest(

        // 목격자 이름
        @NotBlank(message = "목격자 이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
        String name,

        // 목격자 연락처
        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(
                regexp = "^010-\\d{4}-\\d{4}$",
                message = "연락처는 010-0000-0000 형식으로 입력해주세요."
        )
        String phone,

        // 사건과의 관계
        @NotBlank(message = "사건과의 관계를 입력해주세요.")
        @Size(max = 50, message = "사건과의 관계는 50자 이하로 입력해주세요.")
        String relationship,

        // 목격자 등록 시 함께 저장할 최초 진술
        @NotBlank(message = "최초 진술 내용을 입력해주세요.")
        String content
) {
}
