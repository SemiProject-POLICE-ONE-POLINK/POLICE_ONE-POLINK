package com.polink.item.exception;

/** 요청한 물품 번호에 해당하는 데이터를 찾지 못했을 때 사용한다. */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String itemId) {
        super("물품을 찾을 수 없습니다: " + itemId);
    }
}
