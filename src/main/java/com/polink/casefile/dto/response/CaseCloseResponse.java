package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CaseCloseResponse {

    private final String caseId;
    private final CaseStatus status;
    private final LocalDateTime closedAt;

    public CaseCloseResponse(CaseClose caseClose) {
        this.caseId = caseClose.getCaseId();
        this.status = caseClose.getCaseFile().getStatus();
        this.closedAt = caseClose.getClosedAt();
    }
}
