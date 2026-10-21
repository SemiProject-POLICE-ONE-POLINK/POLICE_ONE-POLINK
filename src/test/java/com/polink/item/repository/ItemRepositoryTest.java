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
import com.polink.item.dto.ItemCategoryCountResponse;

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
                null,
                ItemStatus.LOST,
                null,
                null,
                PageRequest.of(0, 10)
        ).getContent().stream().map(Item::getItemId).toList());

        assertEquals(List.of("F-0001"), itemRepository.findItemsByConditions(
                ItemType.FOUND,
                null,
                null,
                null,
                ItemStatus.STORED,
                null,
                null,
                PageRequest.of(0, 10)
        ).getContent().stream().map(Item::getItemId).toList());
    }

    @Test
    @DisplayName("장소와 발생일 범위를 함께 적용해 물품 목록을 조회한다")
    void 장소와_발생일_범위로_물품_목록을_조회한다() {
        // 장소와 날짜가 모두 맞는 물품이다. 검색 결과에 포함되어야 한다.
        itemRepository.save(new Item(
                "L-0101", 1, ItemType.LOST, "검은 지갑", "WALLET_CARD",
                "청솔역에서 잃어버린 지갑", "청솔역 1번 출구",
                LocalDateTime.of(2026, 10, 15, 10, 0), ItemStatus.LOST, null
        ));

        // 장소는 맞지만 종료일 이후에 발생한 물품이다. 검색 결과에서 제외되어야 한다.
        itemRepository.save(new Item(
                "L-0102", 1, ItemType.LOST, "파란 지갑", "WALLET_CARD",
                "청솔역에서 잃어버린 지갑", "청솔역 안내데스크",
                LocalDateTime.of(2026, 11, 1, 0, 0), ItemStatus.LOST, null
        ));

        // 날짜는 맞지만 장소가 다른 물품이다. 검색 결과에서 제외되어야 한다.
        itemRepository.save(new Item(
                "L-0103", 1, ItemType.LOST, "갈색 지갑", "WALLET_CARD",
                "시청역에서 잃어버린 지갑", "시청역 2번 출구",
                LocalDateTime.of(2026, 10, 15, 10, 0), ItemStatus.LOST, null
        ));
        itemRepository.flush();

        List<String> itemIds = itemRepository.findItemsByConditions(
                ItemType.LOST,
                null,
                null,
                "청솔역",
                ItemStatus.LOST,
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0),
                PageRequest.of(0, 10)
        ).getContent().stream().map(Item::getItemId).toList();

        assertEquals(List.of("L-0101"), itemIds);
    }

    @Test
    @DisplayName("카테고리별 물품 개수를 많은 순서로 집계한다")
    void 카테고리별_물품_개수를_많은_순서로_집계한다() {
        // 전자기기 카테고리 물품 두 건을 저장한다.
        itemRepository.save(new Item(
                "L-0001", 1, ItemType.LOST, "검은 휴대폰", "ELECTRONICS",
                "검은색 휴대폰입니다.", "서울역", LocalDateTime.now(),
                ItemStatus.LOST, null
        ));
        itemRepository.save(new Item(
                "F-0001", 2, ItemType.FOUND, "흰색 충전기", "ELECTRONICS",
                "흰색 휴대용 충전기입니다.", "시청역", LocalDateTime.now(),
                ItemStatus.STORED, null
        ));

        // 지갑·카드 카테고리 물품 한 건을 저장한다.
        itemRepository.save(new Item(
                "F-0002", 2, ItemType.FOUND, "갈색 지갑", "WALLET_CARD",
                "교통카드가 들어 있는 지갑입니다.", "강남역", LocalDateTime.now(),
                ItemStatus.STORED, null
        ));

        // 저장한 데이터를 즉시 DB에 반영해 집계 쿼리가 조회할 수 있게 한다.
        itemRepository.flush();

        // 카테고리별 개수 집계 쿼리를 실행한다.
        List<ItemCategoryCountResponse> categoryCounts =
                itemRepository.countItemsByCategory();

        // 물품 수가 많은 ELECTRONICS가 첫 번째 결과여야 한다.
        assertEquals(2, categoryCounts.size());
        assertEquals("ELECTRONICS", categoryCounts.get(0).category());
        assertEquals(2L, categoryCounts.get(0).count());

        // 물품 수가 한 건인 WALLET_CARD가 두 번째 결과여야 한다.
        assertEquals("WALLET_CARD", categoryCounts.get(1).category());
        assertEquals(1L, categoryCounts.get(1).count());
    }
}
