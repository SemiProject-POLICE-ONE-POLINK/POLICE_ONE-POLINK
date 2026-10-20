package com.polink.investigation.entity;

import com.polink.casefile.entity.CaseFile;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// 증거물 Entity
@Entity
@Table(name = "tbl_evidence")
public class Evidence {

    // 증거 번호(EV-사건연도-사건번호 끝 4자리-순번 2자리)
    // ex) EV-2026-0405-01
    @Id
    @Column(name = "evidence_id", length = 25, nullable = false)
    private String evidenceId;

    // 증거물이 속한 사건 번호
    /* DB에서는 tbl_case.case_id를 참조하는 FK.
    *  현재는 사건번호를 String으로 사용하고,
    *  Case Entity가 준비되면 A 담당자와 JPA 연관관계 적용 여부를 협의 예정
    * */

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    // 증거물 이름
    @Column(name = "evidence_name", length = 50, nullable = false)
    private String evidenceName;

    // 증거물 유형(사진, 영상, 문서, 압수물, 기타)
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", length = 30, nullable = false)
    private EvidenceType evidenceType;

    // 증거물 설명
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    // 증거물 확보 일시
    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    // 증거물 확보 장소
    @Column(name = "acquired_location", length = 255, nullable = false)
    private String acquiredLocation;

    // 첨부 파일 저장 경로 (선택)
    @Column(name = "file_path", length = 500)
    private String filePath;

    public Evidence() {
    }

    // 새 증거물 등록
    public Evidence(String evidenceId, CaseFile caseFile, String evidenceName, EvidenceType evidenceType, String description, LocalDateTime acquiredAt, String acquiredLocation) {
        this.evidenceId = evidenceId;                   // 증거 번호
        this.caseFile = caseFile;                           // 사건 번호
        this.evidenceName = evidenceName;               // 증거물 이름
        this.evidenceType = evidenceType;             // 증거물 유형
        this.description = description;                 // 증거물 설명
        this.acquiredAt = acquiredAt;                   // 증거물 확보 일시
        this.acquiredLocation = acquiredLocation;       // 증거물 확보 장소
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public CaseFile getCaseFile() {
        return caseFile;
    }

    public String getEvidenceName() {
        return evidenceName;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public String getAcquiredLocation() {
        return acquiredLocation;
    }

    public String getFilePath() {
        return filePath;
    }

    // 파일 첨부는 선택 기능으로 setter 추가
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
