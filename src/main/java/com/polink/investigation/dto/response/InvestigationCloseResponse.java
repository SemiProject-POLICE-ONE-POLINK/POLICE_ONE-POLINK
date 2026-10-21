package com.polink.investigation.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.investigation.entity.InvestigationLog;

import java.time.LocalDateTime;

// 수사 종결 처리 결과 응답 DTO
public record InvestigationCloseResponse(

        String caseId,              // 사건 번호
        String status,              // 변경된 사건 상태 - INVESTIGATION_DONE, 사건 담당자와 협의 필요
        Integer logId,              // 수사 종결 내용을 남긴 수사기록 번호
        Integer officerId,          // 수사를 종결한 담당 경찰 회원 번호
        String closeReason,         // 수사 종결 사유
        LocalDateTime closedAt      // 수사 종결 일시
) {

    // 상태가 바뀐 사건과 저장된 종결 수사기록으로 응답 생성
    public static InvestigationCloseResponse from(
            CaseFile caseFile,
            InvestigationLog closeLog,
            String closeReason
    ) {
        return new InvestigationCloseResponse(
                caseFile.getCaseId(),
                caseFile.getStatus().name(),
                closeLog.getLogId(),
                closeLog.getOfficer().getUserId(),
                closeReason,
                closeLog.getInvestigatedAt()
        );
    }
}
