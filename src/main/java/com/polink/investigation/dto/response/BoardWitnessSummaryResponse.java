package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;

// 수사보드에 표시할 목격자와 최신 진술 요약
public record BoardWitnessSummaryResponse(

        String witnessId,                   // 상세 조회·추가 진술 등록에 사용
        String name,                        // 목격자 이름
        String phone,                       // 연락처
        String relationship,                // 사건과의 관계
        int statementCount,                 // 전체 진술 개수
        String latestStatementContent,      // 최신 진술 내용
        StatementType latestStatementType   // 최신 진술 유형
) {
    public static BoardWitnessSummaryResponse from(
            Witness witness,
            Statement latestStatement,
            int statementCount
    ) {
        return new BoardWitnessSummaryResponse(
                witness.getWitnessId(),
                witness.getName(),
                witness.getPhone(),
                witness.getRelationship(),
                statementCount,
                latestStatement == null
                        ? null
                        : latestStatement.getContent(),
                latestStatement == null
                        ? null
                        : latestStatement.getStatementType()
        );
    }
}
