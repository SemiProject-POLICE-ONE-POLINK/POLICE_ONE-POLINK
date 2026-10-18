package com.polink.item.entity;

/**
 * 물품의 처리 상태다.
 * 반환 신청과 경찰 처리 기능을 만들 때 HANDOVER_PENDING, RETURNED를 사용한다.
 */
public enum ItemStatus {
    /** 시민이 분실 신고를 막 등록한 상태 */
    LOST,
    /** 시민이 습득물을 보관 중인 상태 */
    STORED,
    /** 반환 신청이 승인되어 인계를 기다리는 상태 */
    HANDOVER_PENDING,
    /** 실제 반환까지 끝난 상태 */
    RETURNED
}
