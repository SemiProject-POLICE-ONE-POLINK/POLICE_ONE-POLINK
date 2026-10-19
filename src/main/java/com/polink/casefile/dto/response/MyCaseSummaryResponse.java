package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import lombok.Getter;

@Getter
public class MyCaseSummaryResponse {

    private final String caseId;
    private final String title;
    private final CaseStatus status;

    public MyCaseSummaryResponse(CaseFile caseFile) {
        this.caseId = caseFile.getCaseId();
        this.title = caseFile.getTitle();
        this.status = caseFile.getStatus();
    }
}
