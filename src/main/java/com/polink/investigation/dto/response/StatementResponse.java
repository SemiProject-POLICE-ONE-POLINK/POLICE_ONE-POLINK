package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Statement;

import java.time.LocalDateTime;

// 진술 1건 응답 DTO
public record StatementResponse(

        Integer statementId,        // 진술 번호
        String content,             // 진술 내용
        String statementType,       // 진술 유형 — 화면의 최초·추가·정정·번복 표시용
        LocalDateTime createdAt     // 진술 등록 일시
) {
    // 진술 Entity를 조회 응답 DTO로 변환
    public static StatementResponse from(Statement statement) {
        return new StatementResponse(
                statement.getStatementId(),
                statement.getContent(),
                statement.getStatementType(),
                statement.getCreatedAt()
        );
    }
}