package com.polink.investigation.dto.response;

import com.polink.investigation.entity.Witness;

import java.util.List;

// 목격자 정보 + 진술 목록 응답 DTO
public record WitnessListResponse(

        String witnessId,                       // 목격자 번호
        String name,                            // 목격자 이름
        String phone,                           // 목격자 연락처
        String relationship,                    // 사건과의 관계
        List<StatementResponse> statements      // 해당 목격자의 최초·추가 진술 목록
) {
    // 목격자 정보와 변환된 진술 목록을 하나의 응답 DTO로 구성
    public static WitnessListResponse from(
            Witness witness,
            List<StatementResponse> statements
    ) {
        return new WitnessListResponse(
                witness.getWitnessId(),
                witness.getName(),
                witness.getPhone(),
                witness.getRelationship(),
                statements
        );
    }
}
