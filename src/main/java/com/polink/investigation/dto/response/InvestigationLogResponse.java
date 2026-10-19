package com.polink.investigation.dto.response;

import com.polink.investigation.entity.InvestigationLog;

import java.time.LocalDateTime;

// 수사기록 등록·조회 응답
public record InvestigationLogResponse(

        Integer logId,               // 수사기록 번호
        Integer officerId,           // 담당 경찰관 회원 번호
        String content,              // 수사 내용
        LocalDateTime investigatedAt // 수사 일시
) {

    // 수사기록 Entity를 응답 DTO로 변환
    public static InvestigationLogResponse from(
            InvestigationLog investigationLog
    ) {
        return new InvestigationLogResponse(
                investigationLog.getLogId(),
                investigationLog.getOfficer().getUserId(),
                investigationLog.getContent(),
                investigationLog.getInvestigatedAt()
        );
    }
}
