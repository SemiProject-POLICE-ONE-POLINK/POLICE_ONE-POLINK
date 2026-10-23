package com.polink.item.exception;

/**
 * 반환 신청의 현재 상태가 요청한 처리와 충돌할 때 사용한다.
 *
 * <p>예: 중복 신청, 이미 승인·반려된 신청의 재처리, 반환 불가능한 물품 상태.</p>
 */
public class ReturnRequestConflictException extends IllegalArgumentException {

    public ReturnRequestConflictException(String message) {
        super(message);
    }
}
