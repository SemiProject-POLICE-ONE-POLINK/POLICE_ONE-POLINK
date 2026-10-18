package com.polink.item.service;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.dto.ItemPageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDate;


/** 실제 DB에 연결하지 않고 ItemService의 번호·조회 처리 규칙을 테스트한다. */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    // 조회·저장 결과를 테스트에서 정할 수 있는 가짜 Repository
    @Mock
    private ItemRepository itemRepository;

    @SuppressWarnings("unused")
    @Mock
    private ItemImageService itemImageService;

    // 가짜 의존성을 주입한 ItemService 생성
    @InjectMocks
    private ItemService itemService;

    private final ItemCreateRequest lostRequest = new ItemCreateRequest(
            ItemType.LOST,
            1,
            "검은색 카드지갑",
            "WALLET_CARD",
            "교통카드와 학생증이 들어 있습니다.",
            "청솔역 1번 출구",
            LocalDate.of(2026, 10, 17)
    );

    // 1. 첫 분실물은 L-0001로 생성되고 LOST 상태로 저장되는지 검사
    @Test
    @DisplayName("첫 분실물은 L-0001로 등록된다")
    void createFirstLostItem() {
        when(itemRepository.findTopByItemTypeOrderByItemIdDesc(ItemType.LOST))
                .thenReturn(Optional.empty());
        when(itemRepository.save(any(Item.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemCreateResponse response = itemService.registerItem(lostRequest);

        assertEquals("L-0001", response.itemId());
        assertEquals(ItemType.LOST, response.itemType());
        assertEquals(ItemStatus.LOST, response.status());
        assertEquals("검은색 카드지갑", response.itemName());
        verify(itemRepository).save(any(Item.class));
    }

    // 2. 습득물은 분실물과 독립적으로 F 번호를 이어서 생성하는지 검사
    @Test
    @DisplayName("습득물은 마지막 F 번호 다음 번호로 등록된다")
    void createNextFoundItem() {
        ItemCreateRequest foundRequest = new ItemCreateRequest(
                ItemType.FOUND, 1, "파란 우산", "CLOTHING_GOODS",
                "나무 손잡이가 달린 우산입니다.", "청솔역", LocalDate.of(2026, 10, 18)
        );
        when(itemRepository.findTopByItemTypeOrderByItemIdDesc(ItemType.FOUND))
                .thenReturn(Optional.of(savedItem("F-0215", ItemType.FOUND, ItemStatus.STORED)));
        when(itemRepository.save(any(Item.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemCreateResponse response = itemService.registerItem(foundRequest);

        assertEquals("F-0216", response.itemId());
        assertEquals(ItemType.FOUND, response.itemType());
        assertEquals(ItemStatus.STORED, response.status());
    }

    // 3. 선택 조건 없이 조회하면 첫 페이지의 전체 물품 목록을 조회하는지 검사
    @Test
    @DisplayName("검색 조건이 없으면 첫 페이지의 물품 목록을 조회한다")
    void getAllItems() {
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository는 이제 List가 아니라 Page 형태로 결과를 반환한다.
        when(itemRepository.findItemsByConditions(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 10),
                1
        ));

        // page=0은 첫 페이지, size=10은 한 페이지에 10개 조회를 의미한다.
        ItemPageResponse response = itemService.getItems(
                null,
                "  ",
                "",
                null,
                0,
                10
        );

        assertEquals(1, response.items().size());
        assertEquals("L-0001", response.items().get(0).itemId());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());

        // 공백 검색어는 null로 정리되고, 페이지 정보가 Repository에 전달되는지 확인한다.
        verify(itemRepository).findItemsByConditions(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 10))
        );
    }
    // 4. 존재하는 번호는 상세 응답으로 변환되는지 검사
    @Test
    @DisplayName("물품 번호로 상세 정보를 조회한다")
    void getItemDetail() {
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(savedItem("L-0001", ItemType.LOST, ItemStatus.LOST)));

        ItemDetailResponse response = itemService.getItemDetail("L-0001");

        assertEquals("L-0001", response.itemId());
        assertEquals("기존 물품", response.itemName());
    }

    // 5. 없는 번호는 404 처리용 ItemNotFoundException이 발생하는지 검사
    @Test
    @DisplayName("존재하지 않는 물품 번호를 조회하면 예외가 발생한다")
    void rejectMissingItem() {
        when(itemRepository.findById("L-9999")).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.getItemDetail("L-9999"));
    }

    // 기존에 저장된 물품 역할을 할 객체 생성. 실제 DB에는 저장하지 않는다.
    private Item savedItem(String itemId, ItemType itemType, ItemStatus itemStatus) {
        return new Item(
                itemId,
                1,
                itemType,
                "기존 물품",
                "ETC",
                "기존 물품 설명",
                "테스트 장소",
                LocalDateTime.of(2026, 10, 17, 10, 0),
                itemStatus,
                null
        );
    }
}
