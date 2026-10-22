package com.polink.user.dto.response;

import com.polink.casefile.entity.CaseType;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        String adminName,                     // 로그인한 관리자 이름
        long waitingCaseCount,                // 대기 중인 사건 수
        long investigatingCaseCount,          // 수사 중인 사건 수
        long closedCaseCount,                 // 종결된 사건 수
        long canceledCaseCount,               // 취소된 사건 수
        long unassignedCaseCount,             // 담당 경찰 배정을 기다리는 사건 수
        List<UnassignedCase> unassignedCases, // 배정 대기 사건 목록

        List<CaseTypeCount> caseTypeCounts     // 사건 유형별 전체 건수
) {
    // 담당 경찰 배정을 기다리는 사건 한 건의 정보
    public record UnassignedCase(
            String caseId,
            String title,
            CaseType caseType,
            LocalDateTime createdAt
    ) {
    }

    // 사건 유형별 사건 건수
    public record CaseTypeCount(
            CaseType caseType,
            long count
    ) {
    }
}
