package com.polink.user.dto.response;

import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;

import java.util.List;


// 경찰 대시보드에 필요한 건수와 본인 배정 사건 목록
public record PoliceDashboardResponse(

        String officerName,                 // 로그인한 경찰 이름
        long investigatingCaseCount,       // 내 배정 사건 중 수사 중인 건수
        long totalAssignedCaseCount,       // 내 배정 사건 전체 건수
        long pendingReturnRequestCount,    // 전체 경찰에게 공통: 처리 대기 중인 반환 신청 건수
        long storedFoundItemCount,         // 전체 경찰에게 공통: 보관 중인 습득물 건수
        List<AssignedCase> assignedCases   // 내 배정 사건 전체 목록
) {

    // 대시보드의 '내 배정 사건' 표에서 한 줄을 표시할 정보
    public record AssignedCase(
            String caseId,       // 사건번호
            String title,        // 사건명
            CaseType caseType,   // 사건 유형
            CaseStatus status    // 사건 상태
    ) {
    }
}
