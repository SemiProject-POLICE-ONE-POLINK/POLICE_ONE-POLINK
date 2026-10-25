package com.polink.investigation.dto;

import java.util.List;

// 수사 가설 체크리스트
public record HypothesisChecklist(

        // AI가 제안한 추가 확인 사항 원문
        String recommendation,

        // 항목별 확인 내용과 체크 상태를 담은 목록
        List<CheckItem> checkItems
) {

    // 체크 항목 한 개의 정보
    public record CheckItem(

            // 같은 가설 안에서 항목을 구분하는 번호
            Integer itemId,

            // AI가 추천하는 추가로 확인할 내용 — ex) 현장 CCTV 확인
            String content,

            // 체크 상태 — true: 완료, false: 미완료
            boolean completed
    ) {
    }
}
