package com.polink.item.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * tbl_item 테이블과 연결되는 분실물·습득물 공통 엔티티다.
 * 분실물과 습득물은 itemType 값만 다르므로 하나의 엔티티로 함께 관리한다.
 */
@Entity
// 실제 DB 테이블명(tbl_item)을 지정한다.
@Table(name = "tbl_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    /** Java에서 직접 만드는 최대 25자 물품 번호 */
    @Id
    // @Id는 이 필드가 테이블의 PK(기본 키)라는 뜻이다.
    @Column(name = "item_id", length = 25)
    private String itemId;

    /** 현재는 등록자 회원 번호만 저장한다. 로그인 기능 구현 뒤 인증 회원 번호를 넣는다. */
    @Column(name = "registrant_id", nullable = false)
    private Integer registrantId;

    @Enumerated(EnumType.STRING)
    // enum을 숫자가 아닌 "LOST", "FOUND" 같은 문자열로 DB에 저장한다.
    @Column(name = "item_type", nullable = false, length = 25)
    private ItemType itemType;

    @Column(name = "item_name", nullable = false, length = 50)
    private String itemName;

    @Column(nullable = false, length = 25)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ItemStatus status;

    @Column(name = "created_at", nullable = false)
    // 사용자가 보내지 않아도 서버가 물품을 만든 시각을 자동 기록한다.
    private LocalDateTime createdAt;

    @SuppressWarnings("unused")
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    /**
     * 등록 API에서 검증된 값을 받아 물품을 만든다.
     * 이미지 업로드 기능은 다음 단계이므로 imagePath는 현재 null로 저장한다.
     */
    public Item(
            String itemId,
            Integer registrantId,
            ItemType itemType,
            String itemName,
            String category,
            String description,
            String location,
            LocalDateTime occurredAt,
            ItemStatus status,
            String imagePath
    ) {
        // 아래 값들은 Controller → Service → Entity 순서로 전달되어 들어온다.
        this.itemId = itemId;
        this.registrantId = registrantId;
        this.itemType = itemType;
        this.itemName = itemName;
        this.category = category;
        this.description = description;
        this.location = location;
        this.occurredAt = occurredAt;
        this.status = status;
        this.imagePath = imagePath;
        // 등록 시각은 클라이언트 시간을 신뢰하지 않고 서버 시간을 사용한다.
        this.createdAt = LocalDateTime.now();
    }

    /*
     * 반환 진행 상황에 맞게 물품 상태를 변경한다.
     */
    public void changeStatus(ItemStatus status) {
        this.status = status;
    }

    /*
     * 실제 인계가 완료된 물품의 반환 완료 시각을 기록한다.
     */
    public void completeReturn() {
        this.status = ItemStatus.RETURNED;
        this.returnedAt = LocalDateTime.now();
    }

    /*
     * 물품의 기본 정보를 수정한다.
     * 물품 유형, 등록자, 상태, 등록 시각은 변경하지 않는다.
     */
    public void updateInfo(
            String itemName,
            String category,
            String description,
            String location,
            LocalDateTime occurredAt
    ) {
        this.itemName = itemName;
        this.category = category;
        this.description = description;
        this.location = location;
        this.occurredAt = occurredAt;
    }

    /*
     * 물품에 연결할 이미지 파일 경로를 기록한다.
     * 파일 저장과 이미지 검증은 ItemImageService가 담당한다.
     *
     * @param imagePath 서버에 저장된 이미지의 경로
     */
    public void attachImage(String imagePath) {
        this.imagePath = imagePath;
    }
}
