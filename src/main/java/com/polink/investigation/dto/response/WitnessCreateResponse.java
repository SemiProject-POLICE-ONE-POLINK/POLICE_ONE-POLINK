package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;

import java.time.LocalDateTime;

public record WitnessCreateResponse(

        String witnessId,                   // 목격자 번호
        String caseId,                      // 목격자가 등록된 사건번호
        String name,                        // 목격자 이름
        String phone,                       // 목격자 연락처
        String relationship,                // 사건과의 관계
        Integer statementId,                // 최초 진술 번호
        String content,                     // 최초 진술 내용
        LocalDateTime statementCreatedAt    // 최초 진술 등록 일시
) {

    // 저장된 목격자와 최초 진술을 하나의 응답 DTO로 변환
    public static WitnessCreateResponse from(
            Witness witness,
            Statement statement
    ) {
        return new WitnessCreateResponse(
                witness.getWitnessId(),
                witness.getCaseFile().getCaseId(),
                witness.getName(),
                witness.getPhone(),
                witness.getRelationship(),
                statement.getStatementId(),
                statement.getContent(),
                statement.getCreatedAt()
        );
    }
}
