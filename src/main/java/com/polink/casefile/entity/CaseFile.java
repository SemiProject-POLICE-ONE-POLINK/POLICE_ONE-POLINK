package com.polink.casefile.entity;

import com.polink.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "tbl_case")
public class CaseFile {

    protected CaseFile() {
    }

    @Id
    @Column(name = "case_id", length = 25, nullable = false)
    private String caseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", length = 30, nullable = false)
    private CaseType caseType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "location", length = 255, nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CaseStatus status;

    @Column(name = "cancel_reason", length = 500, nullable = false)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CaseFile(String caseId, User reporter,
                    String title, CaseType caseType,
                    String description, LocalDateTime occurredAt,
                    String location) {
        this.caseId = caseId;
        this.reporter = reporter;
        this.title = title;
        this.caseType = caseType;
        this.description = description;
        this.occurredAt = occurredAt;
        this.location = location;
        this.status = CaseStatus.WAITING;
        this.cancelReason = "";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void assignOfficer(User officer) {
        if (this.assignedOfficer == null) {
            this.status = CaseStatus.INVESTIGATING;
        }

        this.assignedOfficer = officer;
    }
}