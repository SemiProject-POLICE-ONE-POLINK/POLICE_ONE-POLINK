package com.polink.investigation.dto.response;

import com.polink.casefile.entity.CaseFile;

import java.time.LocalDateTime;

// 수사보드에 표시할 사건 기본 정보
public record BoardCaseResponse(

        String caseId,              // 사건 번호
        String title,               // 사건명
        String caseType,            // 사건 유형
        String status,              // 사건 상태
        LocalDateTime occurredAt,   // 사건 발생 일시
        String location,            // 사건 발생 장소
        String description,         // 사건 설명
        Integer assignedOfficerId   // 담당 경찰관 번호, 미배정이면 null
) {

    public static BoardCaseResponse from(CaseFile caseFile){
        return new BoardCaseResponse(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getCaseType().name(),
                caseFile.getStatus().name(),
                caseFile.getOccurredAt(),
                caseFile.getLocation(),
                caseFile.getDescription(),
                caseFile.getAssignedOfficer() == null
                        ? null
                        : caseFile.getAssignedOfficer().getUserId()
        );
    }
}
