package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.user.entity.UserRole;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class CaseSummaryResponse {

    private final String caseId;
    private final String title;
    private final CaseType caseType;
    private final LocalDateTime occurredAt;
    private final String location;
    private final CaseStatus status;
    private final boolean mine;
    private final boolean locked;

    public CaseSummaryResponse(
            CaseFile caseFile,
            Integer viewerId,
            UserRole viewerRole
    ) {
        this.caseId = caseFile.getCaseId();
        this.title = caseFile.getTitle();
        this.caseType = caseFile.getCaseType();
        this.occurredAt = caseFile.getOccurredAt();
        this.location = caseFile.getLocation();
        this.status = caseFile.getStatus();

        this.mine = Objects.equals(
                caseFile.getReporter().getUserId(),
                viewerId
        );

        this.locked = viewerRole == UserRole.CITIZEN
                && !this.mine;
    }
}
