package com.polink.item.returnrequest.entity;

/*
 * 반환 신청의 처리 상태다.
 * PENDING   : 시민이 신청한 뒤 경찰 처리를 기다리는 상태
 * APPROVED  : 경찰이 승인하여 물품 인계를 기다리는 상태
 * REJECTED  : 경찰이 신청을 반려한 상태
 * COMPLETED : 실제 물품 반환까지 끝난 상태
 */

public enum ReturnRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED
}
