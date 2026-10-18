package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CaseSummaryResponse {

    private final String caseId;
    private final String title;
    private final CaseType caseType;
    private final LocalDateTime occurredAt;
    private final String location;
    private final CaseStatus status;

    public CaseSummaryResponse(CaseFile caseFile) {
        this.caseId = caseFile.getCaseId();
        this.title = caseFile.getTitle();
        this.caseType = caseFile.getCaseType();
        this.occurredAt = caseFile.getOccurredAt();
        this.location = caseFile.getLocation();
        this.status = caseFile.getStatus();
    }
}