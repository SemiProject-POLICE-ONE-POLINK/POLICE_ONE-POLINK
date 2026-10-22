package com.polink.item.returnrequest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * tbl_return_request 테이블과 연결되는 반환 신청 엔티티다.
 */
@Entity
@Table(name = "tbl_return_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequest {

    // DB가 AUTO_INCREMENT로 만드는 반환 신청 번호다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    // 로그인 기능이 완성되면 인증된 시민의 userId가 들어온다.
    @Column(name = "requester_id", nullable = false)
    private Integer requesterId;

    // 신청자가 등록한 분실물 번호다. 예: L-0001
    @Column(name = "lost_item_id", nullable = false, length = 25)
    private String lostItemId;

    // 반환을 요청한 습득물 번호다. 예: F-0001
    @Column(name = "found_item_id", nullable = false, length = 25)
    private String foundItemId;

    // 승인·반려를 처리한 경찰관 번호다. 최초 신청 시에는 없다.
    @Column(name = "processed_by")
    private Integer processedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ReturnRequestStatus status;

    @Column(name = "request_reason", nullable = false, columnDefinition = "TEXT")
    private String requestReason;

    // 반려 전에는 빈 문자열로 저장한다.
    @Column(name = "reject_reason", nullable = false, length = 500)
    private String rejectReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /*
     * 실제 물품 인계를 완료한 경찰관 번호다.
     * 승인 처리자와 실제 인계 완료 처리자가 다를 수 있으므로 별도로 저장한다.
     */
    @Column(name = "completed_by")
    private Integer completedBy;

    /*
     * 실제 물품 인계가 완료된 서버 시각이다.
     * 승인·반려 처리 시각(processedAt)과 구분해 저장한다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    /*
     * 시민이 반환 신청을 생성할 때 사용한다.
     */
    public ReturnRequest(
            Integer requesterId,
            String lostItemId,
            String foundItemId,
            String requestReason
    ) {
        this.requesterId = requesterId;
        this.lostItemId = lostItemId;
        this.foundItemId = foundItemId;
        this.status = ReturnRequestStatus.PENDING;
        this.requestReason = requestReason;
        this.rejectReason = "";
        this.createdAt = LocalDateTime.now();
    }

    /*
     * 경찰관이 대기 중인 반환 신청을 승인한다.
     */
    public void approve(Integer policeId) {
        this.status = ReturnRequestStatus.APPROVED;
        this.processedBy = policeId;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = "";
    }

    /*
     * 경찰관이 대기 중인 반환 신청을 반려한다.
     */
    public void reject(Integer policeId, String rejectReason) {
        this.status = ReturnRequestStatus.REJECTED;
        this.processedBy = policeId;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
    }

    /*
     * 실제 물품 인계가 끝난 반환 신청을 완료 처리한다.
     * 완료 담당 경찰관과 완료 시각을 승인·반려 처리 정보와 분리해 보관한다.
     */
    public void complete(Integer policeId) {
        this.status = ReturnRequestStatus.COMPLETED;
        this.completedBy = policeId;
        this.completedAt = LocalDateTime.now();
    }

}
