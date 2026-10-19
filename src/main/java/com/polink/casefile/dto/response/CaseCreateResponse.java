package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseStatus;
import lombok.Getter;

@Getter
public class CaseCreateResponse {

    private final String caseId;
    private final CaseStatus status;

    public CaseCreateResponse(String caseId, CaseStatus status) {
        this.caseId = caseId;
        this.status = status;
    }
}
