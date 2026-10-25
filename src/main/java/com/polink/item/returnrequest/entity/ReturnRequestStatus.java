package com.polink.item.returnrequest.entity;

/*
 * 반환 신청의 처리 상태다.
 * PENDING   : 시민이 신청한 뒤 경찰 처리를 기다리는 상태
 * REQUESTED : 이전 DB에 남아 있을 수 있는 PENDING의 과거 저장값
 * APPROVED  : 경찰이 승인하여 물품 인계를 기다리는 상태
 * REJECTED  : 경찰이 신청을 반려한 상태
 * COMPLETED : 실제 물품 반환까지 끝난 상태
 */

public enum ReturnRequestStatus {
    PENDING,

    /*
     * 이전 화면/DB가 사용했던 값이다. 현재 API 계약의 정식 대기 상태는 PENDING이다.
     *
     * DB에 REQUESTED가 남아 있는 상태에서 이 값을 제거하면, JPA가 반환 신청 목록을
     * 읽는 단계에서 예외를 내므로 SQL 정리 전에도 목록 전체가 실패하지 않게 한다.
     * 새 반환 신청은 항상 PENDING으로 저장하며, 프론트 화면에서는 PENDING으로
     * 정규화해 처리한다.
     */
    @Deprecated
    REQUESTED,
    APPROVED,
    REJECTED,
    COMPLETED;

    /**
     * REQUESTED는 과거 DB에 남아 있는 PENDING의 이전 이름이다.
     * 화면에서는 이미 PENDING으로 보정하므로, 상태 변경 업무도 두 값을
     * 같은 "처리 대기" 상태로 취급해야 화면과 서버 규칙이 어긋나지 않는다.
     */
    public boolean isPendingForProcessing() {
        return this == PENDING || this == REQUESTED;
    }
}
