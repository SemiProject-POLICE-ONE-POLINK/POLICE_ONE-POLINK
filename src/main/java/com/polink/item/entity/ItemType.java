package com.polink.item.entity;

/**
 * 물품이 분실물인지(LOST), 습득물인지(FOUND)를 구분한다.
 * DB의 tbl_item.item_type 컬럼에는 enum 이름 그대로 저장된다.
 */
public enum ItemType {
    LOST,
    FOUND
}
