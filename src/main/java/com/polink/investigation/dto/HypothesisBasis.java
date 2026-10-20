package com.polink.investigation.dto;

import java.util.List;

// 채택된 가설이 어떤 자료를 근거로 하는지 번호만 묶어서 보관

public record HypothesisBasis(
        List<String> evidenceIds,        // 가설의 근거로 사용된 증거물 번호(없을 시 빈 목록 반환)
        List<Integer> statementIds      // 가설의 근거로 사용된 진술 번호(없을 시 빈 목록 반환)
) {
}
