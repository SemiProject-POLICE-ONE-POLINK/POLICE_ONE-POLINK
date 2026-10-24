package com.polink.investigation.entity;

import com.polink.casefile.entity.CaseFile;
import com.polink.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// 사건에 대한 수사 가설을 추론
@Entity
@Table(name = "tbl_hypothesis")
public class Hypothesis {

    // 수사 가설 번호
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hypothesis_id", nullable = false)
    private Integer hypothesisId;

    // 이 가설이 속한 사건
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    // 가설을 채택한 경찰
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adopted_by", nullable = false)
    private User adoptedBy;

    // 수사 가설의 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // AI가 가설을 제시한 판단 근거
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    // 추가로 확인할 수사 사항
    @Column(name = "additional_recommendation", columnDefinition = "TEXT")
    private String additionalRecommendation;

    // AI 신뢰도: 0~100. 저장 전 Service에서 범위를 검증한다.
    @Column(name = "confidence")
    private Integer confidence;

    // 현재는 AI가 생성한 가설만 채택하므로 출처를 AI로 고정
    @Column(name = "source", length = 10, nullable = false)
    private String source = "AI";

    // 근거가 된 증거물·진술의 ID 목록을 JSON 문자열로 저장
    // ID가 실제로 이 사건에 속하는지는 저장 전 Service에서 검증한다.
    @Column(name = "basis_json", columnDefinition = "TEXT")
    private String basisJson;

    // 경찰관이 가설을 채택한 시각
    @Column(name = "adopted_at", nullable = false)
    private LocalDateTime adoptedAt;

    // JPA가 DB 조회 시 객체를 생성하기 위해 필요한 기본 생성자
    // 일반 코드에서 값이 없는 가설을 쉽게 만들지 않도록 protected로 제한
    protected Hypothesis() {
    }

    // 경찰이 AI 가설을 채택할 때 사용한다.
    // adoptedBy에는 요청값이 아닌 인증된 경찰 User를 전달한다.
    public Hypothesis(CaseFile caseFile, User adoptedBy, String content, String reason, String additionalRecommendation, Integer confidence, String basisJson) {
        this.caseFile = caseFile;
        this.adoptedBy = adoptedBy;
        this.content = content;
        this.reason = reason;
        this.additionalRecommendation = additionalRecommendation;
        this.confidence = confidence;
        this.basisJson = basisJson;
    }

    // INSERT 직전에 채택 시각을 설정한
    @PrePersist
    protected void onCreate() {
        if (adoptedAt == null) {
            adoptedAt = LocalDateTime.now();
        }
    }

    public Integer getHypothesisId() {
        return hypothesisId;
    }

    public CaseFile getCaseFile() {
        return caseFile;
    }

    public User getAdoptedBy() {
        return adoptedBy;
    }

    public String getContent() {
        return content;
    }

    public String getReason() {
        return reason;
    }

    public String getAdditionalRecommendation() {
        return additionalRecommendation;
    }

    // 변경한 체크 상태가 담긴 JSON으로 값 변경
    public void updateChecklist(String checklistJson) {
        this.additionalRecommendation = checklistJson;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }

    public String getBasisJson() {
        return basisJson;
    }

    public LocalDateTime getAdoptedAt() {
        return adoptedAt;
    }
}