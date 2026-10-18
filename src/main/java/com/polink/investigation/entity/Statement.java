package com.polink.investigation.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// 최초·추가 진술 Entity
@Entity
@Table(name = "tbl_statement")
public class Statement {

    // 진술 번호 — DB에서 자동 생성
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "statement_id", nullable = false)
    private Integer statementId;

    // 진술한 목격자 번호
    // 같은 목격자가 추가 진술 시 해당 번호는 동일
    @Column(name = "witness_id", length = 25, nullable = false)
    private String witnessId;

    // 진술 내용
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // 진술 유형 — 최초·추가·정정·번복 구분
    @Column(name = "statement_type", length = 30, nullable = false)
    private String statementType;

    // 진술 등록 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Statement() {
    }

    public Statement(String witnessId, String content, String statementType, LocalDateTime createdAt) {
        this.witnessId = witnessId;
        this.content = content;
        this.statementType = statementType;
        this.createdAt = createdAt;
    }

    public Integer getStatementId() {
        return statementId;
    }

    public String getWitnessId() {
        return witnessId;
    }

    public String getContent() {
        return content;
    }

    public String getStatementType() {
        return statementType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
