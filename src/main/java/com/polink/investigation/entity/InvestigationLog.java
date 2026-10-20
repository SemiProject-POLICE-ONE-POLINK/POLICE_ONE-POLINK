package com.polink.investigation.entity;

import com.polink.casefile.entity.CaseFile;
import com.polink.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// 사건별 수사 활동 기록
@Entity
@Table(name = "tbl_investigation_log")
public class InvestigationLog {

    // 수사기록 번호 — DB에서 자동 생성
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Integer logId;

    // 수사기록이 속한 사건 번호
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    // 수사를 진행한 경찰관 회원 번호
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private User officer;

    // 수사 활동 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 수사 활동 일시
    @Column(name = "investigated_at", nullable = false)
    private LocalDateTime investigatedAt;

    public InvestigationLog() {
    }

    public InvestigationLog(CaseFile caseFile, User officer, String content, LocalDateTime investigatedAt) {
        this.caseFile = caseFile;
        this.officer = officer;
        this.content = content;
        this.investigatedAt = investigatedAt;
    }

    public Integer getLogId() {
        return logId;
    }

    public CaseFile getCaseFile() {
        return caseFile;
    }

    public User getOfficer() {
        return officer;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getInvestigatedAt() {
        return investigatedAt;
    }
}