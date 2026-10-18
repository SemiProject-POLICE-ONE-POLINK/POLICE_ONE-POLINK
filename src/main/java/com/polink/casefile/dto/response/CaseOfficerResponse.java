package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import lombok.Getter;

@Getter
public class CaseOfficerResponse {

    private final String caseId;
    private final Integer assignedOfficerId;
    private final CaseStatus status;

    public CaseOfficerResponse(CaseFile caseFile) {
        this.caseId = caseFile.getCaseId();
        this.assignedOfficerId =
                caseFile.getAssignedOfficer().getUserId();
        this.status = caseFile.getStatus();
    }
}