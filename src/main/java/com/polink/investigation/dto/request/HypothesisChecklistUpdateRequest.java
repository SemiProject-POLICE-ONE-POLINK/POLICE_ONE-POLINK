package com.polink.investigation.dto.request;

import jakarta.validation.constraints.NotNull;

// 추가 확인 항목의 체크 상태 변경 요청
public record HypothesisChecklistUpdateRequest(

        // true: 완료 체크, false: 체크 해제
        @NotNull(message = "체크 상태를 확인해주세요.")
        Boolean completed
) {
}
