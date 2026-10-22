package com.polink.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.polink.item.dto.ItemMatchListResponse;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.repository.ItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/*
 * AI 물품 매칭의 후보 선정·점수·권한 검증 규칙을 검사한다.
 */
@ExtendWith(MockitoExtension.class)
class ItemMatchServiceTest {

    // 실제 DB 대신 후보 물품을 준비할 수 있는 가짜 Repository다.
    @Mock
    private ItemRepository itemRepository;

    // 외부 OpenAI API를 호출하지 않고 규칙 기반 점수를 검증하기 위한 구현체다.
    private final ItemMatchAiClient ruleBasedItemMatchAiClient =
            new RuleBasedItemMatchAiClient();

    // 같은 카테고리의 보관 중 습득물이 후보로 반환되는지 검사한다.
    @Test
    void findsStoredFoundItemWithSameCategory() {
        Item lostItem = createItem(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST,
                "검은 카드지갑",
                "WALLET_CARD",
                "청솔역"
        );

        Item matchedFoundItem = createItem(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.STORED,
                "검은 지갑",
                "WALLET_CARD",
                "청솔역 1번 출구"
        );

        // 요청한 분실물과 보관 중 습득물 후보를 Repository가 반환하도록 설정한다.
        given(itemRepository.findById("L-0001"))
                .willReturn(java.util.Optional.of(lostItem));
        given(itemRepository.findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                ItemType.FOUND,
                ItemStatus.STORED
        )).willReturn(List.of(matchedFoundItem));

        ItemMatchListResponse result = new ItemMatchService(
                itemRepository,
                ruleBasedItemMatchAiClient
        ).findMatches(1, "L-0001");

        // 후보는 한 건이며, 습득물 번호가 F-0001인지 확인한다.
        assertEquals(1, result.matches().size());
        assertEquals("F-0001", result.matches().get(0).foundItemId());

        // 매칭 사유에 카테고리 일치 근거가 포함되어야 한다.
        assertTrue(
                result.matches().get(0).reason().contains("카테고리가 일치합니다.")
        );

        // 카테고리(50점)와 장소(20점)가 일치하므로 70점이어야 한다.
        assertEquals(70, result.matches().get(0).score());

        // 후보 습득물의 기본 정보와 현재 상태도 함께 반환되어야 한다.
        assertEquals("검은 지갑", result.matches().get(0).foundItemName());
        assertEquals("WALLET_CARD", result.matches().get(0).foundItemCategory());
        assertEquals(ItemStatus.STORED, result.matches().get(0).foundItemStatus());

        // 테스트 데이터에는 이미지가 없으므로 이미지 경로는 null이다.
        assertNull(result.matches().get(0).foundImagePath());

        // 보관 중 습득물 후보 조회가 정확한 조건으로 호출됐는지 확인한다.
        verify(itemRepository)
                .findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                        ItemType.FOUND,
                        ItemStatus.STORED
                );
    }

    // 다른 사용자의 분실물은 후보 매칭을 요청할 수 없는지 검사한다.
    @Test
    void rejectsAnotherUsersLostItem() {
        Item anotherUsersLostItem = createItem(
                "L-0002",
                2,
                ItemType.LOST,
                ItemStatus.LOST,
                "파란 우산",
                "ETC",
                "청솔역"
        );

        given(itemRepository.findById("L-0002"))
                .willReturn(java.util.Optional.of(anotherUsersLostItem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ItemMatchService(
                        itemRepository,
                        ruleBasedItemMatchAiClient
                ).findMatches(1, "L-0002")
        );

        // 본인 물품이 아니므로 403 Forbidden이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검증을 위해 분실물 정보 조회는 한 번 수행되어야 한다.
        verify(itemRepository).findById("L-0002");

        // 다른 사람 물품임을 확인한 뒤 중단되므로 후보 조회는 호출되면 안 된다.
        verify(itemRepository, never())
                .findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                        ItemType.FOUND,
                        ItemStatus.STORED
                );
    }

    // 존재하지 않는 분실물 번호로 매칭하면 404 예외가 발생하는지 검사한다.
    @Test
    void rejectsUnknownLostItem() {
        given(itemRepository.findById("L-9999"))
                .willReturn(java.util.Optional.empty());

        assertThrows(
                ItemNotFoundException.class,
                () -> new ItemMatchService(
                        itemRepository,
                        ruleBasedItemMatchAiClient
                ).findMatches(1, "L-9999")
        );

        // 분실물 조회에서 중단되므로 습득물 후보 조회는 호출되면 안 된다.
        verify(itemRepository, never())
                .findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                        ItemType.FOUND,
                        ItemStatus.STORED
                );
    }

    // 유사점이 전혀 없는 습득물은 후보에서 제외되는지 검사한다.
    @Test
    void returnsEmptyMatchesWhenNoSimilarFoundItemExists() {
        Item lostItem = createItem(
                "L-0003",
                1,
                ItemType.LOST,
                ItemStatus.LOST,
                "검은 카드지갑",
                "WALLET_CARD",
                "청솔역"
        );

        Item unrelatedFoundItem = createItem(
                "F-0003",
                2,
                ItemType.FOUND,
                ItemStatus.STORED,
                "파란 우산",
                "ETC",
                "강남역"
        );

        given(itemRepository.findById("L-0003"))
                .willReturn(java.util.Optional.of(lostItem));
        given(itemRepository.findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                ItemType.FOUND,
                ItemStatus.STORED
        )).willReturn(List.of(unrelatedFoundItem));

        ItemMatchListResponse result = new ItemMatchService(
                itemRepository,
                ruleBasedItemMatchAiClient
        ).findMatches(1, "L-0003");

        assertEquals(0, result.matches().size());
    }

    // 점수가 높은 습득물이 낮은 습득물보다 먼저 정렬되는지 검사한다.
    @Test
    void sortsMatchesBySimilarityScoreDescending() {
        Item lostItem = createItem(
                "L-0004",
                1,
                ItemType.LOST,
                ItemStatus.LOST,
                "검은 카드지갑",
                "WALLET_CARD",
                "청솔역"
        );

        // 카테고리만 일치하므로 50점 후보다.
        Item lowerScoreFoundItem = createItem(
                "F-0004",
                2,
                ItemType.FOUND,
                ItemStatus.STORED,
                "갈색 지갑",
                "WALLET_CARD",
                "강남역"
        );

        // 카테고리와 장소가 일치하므로 70점 후보다.
        Item higherScoreFoundItem = createItem(
                "F-0005",
                3,
                ItemType.FOUND,
                ItemStatus.STORED,
                "파란 지갑",
                "WALLET_CARD",
                "청솔역 광장"
        );

        given(itemRepository.findById("L-0004"))
                .willReturn(java.util.Optional.of(lostItem));
        given(itemRepository.findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                ItemType.FOUND,
                ItemStatus.STORED
        )).willReturn(List.of(lowerScoreFoundItem, higherScoreFoundItem));

        ItemMatchListResponse result = new ItemMatchService(
                itemRepository,
                ruleBasedItemMatchAiClient
        ).findMatches(1, "L-0004");

        // 70점 후보 F-0005가 50점 후보 F-0004보다 먼저 와야 한다.
        assertEquals("F-0005", result.matches().get(0).foundItemId());
        assertEquals("F-0004", result.matches().get(1).foundItemId());

        // 정렬뿐 아니라 실제 점수도 응답에 포함되어야 한다.
        assertEquals(70, result.matches().get(0).score());
        assertEquals(50, result.matches().get(1).score());
    }

    // 장소만 유사한 20점 후보는 최소 기준 50점에 미달하므로 제외되는지 검사한다.
    @Test
    void excludesCandidateBelowMinimumMatchScore() {
        Item lostItem = createItem(
                "L-0005",
                1,
                ItemType.LOST,
                ItemStatus.LOST,
                "검은 카드지갑",
                "WALLET_CARD",
                "청솔역"
        );

        // 장소만 일치하고 카테고리·물품명은 다르므로 20점 후보다.
        Item lowScoreFoundItem = createItem(
                "F-0006",
                2,
                ItemType.FOUND,
                ItemStatus.STORED,
                "파란 우산",
                "CLOTHING_GOODS",
                "청솔역 광장"
        );

        given(itemRepository.findById("L-0005"))
                .willReturn(java.util.Optional.of(lostItem));
        given(itemRepository.findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                ItemType.FOUND,
                ItemStatus.STORED
        )).willReturn(List.of(lowScoreFoundItem));

        ItemMatchListResponse result = new ItemMatchService(
                itemRepository,
                ruleBasedItemMatchAiClient
        ).findMatches(1, "L-0005");

        // 최소 50점에 미달한 후보는 응답 목록에 포함되면 안 된다.
        assertEquals(0, result.matches().size());
    }

    // 테스트용 분실물·습득물 엔티티를 만드는 보조 메서드다.
    private Item createItem(
            String itemId,
            Integer registrantId,
            ItemType itemType,
            ItemStatus status,
            String itemName,
            String category,
            String location
    ) {
        return new Item(
                itemId,
                registrantId,
                itemType,
                itemName,
                category,
                "매칭 테스트용 설명입니다.",
                location,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                status,
                null
        );
    }
}