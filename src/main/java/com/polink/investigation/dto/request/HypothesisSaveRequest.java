package com.polink.investigation.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

// 경찰이 검토 후 선택한 가설을 저장할 때 사용하는 요청 DTO
public record HypothesisSaveRequest(

        // 저장할 가설 내용
        @NotBlank(message = "선택한 가설의 내용이 누락되었습니다.")
        String content,

        // 가설의 판단 근거
        @NotBlank(message = "선택한 가설의 분석 근거가 누락되었습니다.")
        String reason,

        // 추가 수사 권고 사항(선택)
        String additionalRecommendation,

        // 생성된 가설에 대한 신뢰도
        @NotNull(message = "선택한 가설의 신뢰도 정보가 누락되었습니다.")
        @Min(value = 0, message = "신뢰도 값이 올바르지 않습니다.")
        @Max(value = 100, message = "신뢰도 값이 올바르지 않습니다.")
        Integer confidence,

        // 가설의 판단 근거에 대한 증거물 정보
        @NotNull(message = "증거물 근거 정보가 누락되었습니다.")
        List<String> evidenceIds,

        // 가설의 판단 근거에 대한 진술 정보
        @NotNull(message = "진술 근거 정보가 누락되었습니다.")
        List<Integer> statementIds
) {
}
