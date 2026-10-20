package com.polink.investigation.entity;

import com.polink.casefile.entity.CaseFile;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// 목격자 Entity
@Entity
@Table(name = "tbl_witness")
public class Witness {

    // 목격자 번호: (W-사건연도-사건번호-순번2자리)
    // ex) W-2026-0405-01
    @Id
    @Column(name = "witness_id", length = 25, nullable = false)
    private String witnessId;

    // 목격자가 속한 사건 번호
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    // 목격자 이름
    @Column(name = "name", length = 50, nullable = false)
    private String name;

    // 목격자 연락처
    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    // 사건과의 관계
    @Column(name = "relationship", length = 50, nullable = false)
    private String relationship;

    // 목격자 등록 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Witness() {
    }

    public Witness(String witnessId, CaseFile casefile, String name, String phone, String relationship, LocalDateTime createdAt) {
        this.witnessId = witnessId;
        this.caseFile = casefile;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
        this.createdAt = createdAt;
    }

    public String getWitnessId() {
        return witnessId;
    }

    public CaseFile getCaseFile() {
        return caseFile;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getRelationship() {
        return relationship;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
