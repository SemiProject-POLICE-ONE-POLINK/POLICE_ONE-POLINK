package com.polink.item.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;


// Verifies that tbl_item entities are stored and queried in the H2 test database.
@SuppressWarnings("NonAsciiCharacters")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ItemRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Test
    @DisplayName("분실물과 습득물을 저장하고 유형별 마지막 번호를 조회한다")
     void 분실물과_습득물이_각각_DB에_저장되고_유형별_마지막_번호를_조회한다() {
        Item lostItem = new Item(
                "L-0001", 1, ItemType.LOST, "검은 지갑", "WALLET",
                "카드가 들어 있는 검은 지갑", "서울역", LocalDateTime.now(),
                ItemStatus.LOST, null
        );
        Item foundItem = new Item(
                "F-0001", 2, ItemType.FOUND, "파란 우산", "ETC",
                "파란색 장우산", "시청역", LocalDateTime.now(),
                ItemStatus.STORED, "C:/uploads/item.png"
        );

        itemRepository.save(lostItem);
        itemRepository.save(foundItem);
        itemRepository.flush();

        assertTrue(itemRepository.findById("L-0001").isPresent());
        assertTrue(itemRepository.findById("F-0001").isPresent());
        assertEquals("L-0001", itemRepository
                .findTopByItemTypeOrderByItemIdDesc(ItemType.LOST).orElseThrow().getItemId());
        assertEquals("F-0001", itemRepository
                .findTopByItemTypeOrderByItemIdDesc(ItemType.FOUND).orElseThrow().getItemId());
    }

    @Test
    @DisplayName("유형 카테고리 검색어 상태 조건으로 물품 목록을 조회한다")
    void 유형_카테고리_검색어_상태_조건으로_물품_목록을_조회한다() {
        itemRepository.save(new Item(
                "L-0001", 1, ItemType.LOST, "검은 지갑", "WALLET_CARD",
                "교통카드가 들어 있는 지갑", "서울역", LocalDateTime.now(),
                ItemStatus.LOST, null
        ));
        itemRepository.save(new Item(
                "F-0001", 2, ItemType.FOUND, "파란 우산", "CLOTHING_GOODS",
                "분실물 보관함에서 보관 중인 우산", "시청역", LocalDateTime.now(),
                ItemStatus.STORED, null
        ));
        itemRepository.flush();

        assertEquals(List.of("L-0001"), itemRepository.findItemsByConditions(
                ItemType.LOST,
                "WALLET_CARD",
                "교통카드",
                ItemStatus.LOST,
                PageRequest.of(0, 10)
        ).getContent().stream().map(Item::getItemId).toList());

        assertEquals(List.of("F-0001"), itemRepository.findItemsByConditions(
                ItemType.FOUND,
                null,
                null,
                ItemStatus.STORED,
                PageRequest.of(0, 10)
        ).getContent().stream().map(Item::getItemId).toList());
    }
}
