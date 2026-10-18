package com.polink.item.repository;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** tbl_item의 저장·조회 기능을 Spring Data JPA로 제공한다. */
public interface ItemRepository extends JpaRepository<Item, String> {
    // JpaRepository<Item, String>의 의미
    // 1) Item: 이 Repository가 관리할 Entity
    // 2) String: Item의 PK(itemId) 타입
    // save(), findById(), findAll() 같은 기본 메서드는 Spring Data JPA가 자동으로 만들어 준다.

    /**
     * 분실물(LOST) 또는 습득물(FOUND) 중 번호가 가장 큰 물품 한 건을 찾는다.
     * 예: LOST의 마지막 번호가 L-0115이면 다음 분실물 번호는 L-0116이 된다.
     */
    Optional<Item> findTopByItemTypeOrderByItemIdDesc(ItemType itemType);
}
