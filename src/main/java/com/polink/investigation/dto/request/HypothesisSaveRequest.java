package com.polink.investigation.dto.request;

import jakarta.validation.constraints.NotBlank;

// 경찰이 검토 후 선택한 가설을 저장할 때 사용하는 요청 DTO
public record HypothesisSaveRequest(

        // 생성 응답에서 받은 임시 후보 번호
        @NotBlank(message = "선택한 가설 정보를 받지 못했습니다. 다시 시도해 주세요.")
        String candidateId
) {
}
