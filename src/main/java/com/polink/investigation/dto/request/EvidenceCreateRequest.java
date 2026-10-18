package com.polink.investigation.dto.request;

import com.polink.investigation.entity.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 증거 등록 Request DTO
public record EvidenceCreateRequest(

        @NotBlank(message = "증거물의 이름을 입력해주세요.")
        @Size(max = 50, message = "증거물 이름은 50자 내로 입력해주세요.")
        String evidenceName,            // 증거물 이름

        @NotNull(message = "증거물 유형을 선택해주세요.")
        EvidenceType evidenceType,      // 증거물 유형

        @NotBlank(message = "설명을 입력해주세요.")
        String description,             // 증거물 설명

        @NotBlank(message = "확보 장소를 입력해주세요.")
        @Size(max = 255)
        String acquiredLocation        // 증거물 확보 장소
) {
}
