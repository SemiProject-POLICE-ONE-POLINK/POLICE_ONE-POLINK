package com.polink.casefile.entity;

import com.polink.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "tbl_case_close")
public class CaseClose {

    protected CaseClose() {
    }

    @Id
    @Column(name = "case_id", length = 25, nullable = false)
    private String caseId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "closed_by", nullable = false)
    private User closedBy;

    @Column(name = "close_reason", nullable = false, columnDefinition = "TEXT")
    private String closeReason;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    public CaseClose(
            CaseFile caseFile,
            User closedBy,
            String closeReason) {
        this.caseId = caseFile.getCaseId();
        this.caseFile = caseFile;
        this.closedBy = closedBy;
        this.closeReason = closeReason;
    }

    @PrePersist
    protected void onCreate() {
        this.closedAt = LocalDateTime.now();
    }
}