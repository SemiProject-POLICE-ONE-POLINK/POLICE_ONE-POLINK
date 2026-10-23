package com.polink.item.exception;

/**
 * 반환 신청 또는 반환 신청에 연결된 물품을 찾지 못했을 때 사용한다.
 *
 * <p>단순 입력 오류(400)와 실제 데이터 부재(404)를 구분해
 * API 명세의 오류 상태 코드를 정확하게 반환하기 위한 예외다.</p>
 */
public class ReturnRequestNotFoundException extends IllegalArgumentException {

    public ReturnRequestNotFoundException(String message) {
        super(message);
    }
}
