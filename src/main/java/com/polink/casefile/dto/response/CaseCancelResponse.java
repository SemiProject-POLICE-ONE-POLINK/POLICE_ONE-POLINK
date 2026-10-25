package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import lombok.Getter;

@Getter
public class CaseCancelResponse {

    private final String caseId;
    private final CaseStatus status;
    private final String cancelReason;

    public CaseCancelResponse(CaseFile caseFile) {
        this.caseId = caseFile.getCaseId();
        this.status = caseFile.getStatus();
        this.cancelReason = caseFile.getCancelReason();
    }
}
