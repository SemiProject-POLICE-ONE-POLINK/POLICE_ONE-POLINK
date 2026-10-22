package com.polink.user.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;

import java.time.LocalDateTime;

// 시민 마이페이지 내 사건 목록(요약)
public record MyCaseSummaryResponse(

        String caseId,             // 사건 번호
        String title,              // 사건명
        CaseType caseType,         // 사건 유형
        CaseStatus status,         // 현재 사건 상태
        LocalDateTime createdAt,   // 사건 접수 일시
        String location,           // 사건 발생 장소
        String cancelReason,       // 접수 취소 사유
        LocalDateTime closedAt     // 종결 일시 - 미종결이면 null
) {

    // CaseFile에서 마이페이지에 필요한 값만 변환
    public MyCaseSummaryResponse(CaseFile caseFile, LocalDateTime closedAt) {
        this(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getCaseType(),
                caseFile.getStatus(),
                caseFile.getCreatedAt(),
                caseFile.getLocation(),
                caseFile.getCancelReason(),
                closedAt
        );
    }
}
