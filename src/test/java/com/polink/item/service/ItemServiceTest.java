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

    // 4. 로그인한 사용자가 등록한 물품 목록만 조회하는지 검사
    @Test
    @DisplayName("내가 등록한 물품 목록을 페이지 단위로 조회한다")
    void getMyItems() {
        // savedItem()은 등록자 ID를 1로 만들어 주므로,
        // 로그인한 사용자 ID를 1이라고 가정한 테스트 데이터로 사용한다.
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository가 등록자 ID=1의 물품 한 건을 반환하도록 설정한다.
        when(itemRepository.findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                eq(1),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 10),
                1
        ));

        // 로그인한 사용자 ID=1, 첫 페이지, 한 페이지 10개 조회를 요청한다.
        ItemPageResponse response = itemService.getMyItems(1, 0, 10);

        // 응답에 등록자 1의 물품 한 건이 포함되는지 확인한다.
        assertEquals(1, response.items().size());
        assertEquals("L-0001", response.items().get(0).itemId());
        assertEquals(0, response.page());
        assertEquals(10, response.size());
        assertEquals(1, response.totalElements());

        // Service가 Repository에 사용자 ID=1과 올바른 페이지 정보를 전달했는지 확인한다.
        verify(itemRepository).findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                eq(1),
                eq(PageRequest.of(0, 10))
        );
    }

    // 5. 내 등록 물품이 없어도 오류 대신 빈 목록을 반환하는지 검사
    @Test
    @DisplayName("내가 등록한 물품이 없으면 빈 목록을 반환한다")
    void getMyItemsReturnsEmptyList() {
        // Repository가 조회 결과 없음(빈 목록)을 반환하도록 설정한다.
        when(itemRepository.findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                eq(1),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        ));

        // 사용자 ID=1의 내 물품 목록을 조회한다.
        ItemPageResponse response = itemService.getMyItems(1, 0, 10);

        // 빈 목록이어도 정상 응답 객체가 반환되어야 한다.
        assertTrue(response.items().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());
    }

    // 6. 로그인 사용자 ID와 페이지 입력값을 검증하는지 검사
    @Test
    @DisplayName("내 등록 물품 조회의 사용자 ID와 페이지 조건을 검증한다")
    void rejectInvalidMyItemsConditions() {
        // 로그인한 사용자 정보가 없으면 조회할 수 없다.
        assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getMyItems(null, 0, 10)
        );

        // 사용자 ID는 1 이상이어야 한다.
        assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getMyItems(0, 0, 10)
        );

        // 페이지 번호는 0 이상이어야 한다.
        assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getMyItems(1, -1, 10)
        );

        // 한 페이지 크기는 1 이상이어야 한다.
        assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getMyItems(1, 0, 0)
        );

        // 한 페이지 크기는 최대 100개까지만 허용한다.
        assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getMyItems(1, 0, 101)
        );

        // 잘못된 입력값은 Repository 조회까지 진행되면 안 된다.
        verifyNoInteractions(itemRepository);
    }

    // 여러 테스트에서 공통으로 사용할 가짜 Item 객체를 만든다.
    // 실제 DB에 저장하지 않고, Service와 Repository 테스트 데이터로만 사용한다.
    private Item savedItem(
            String itemId,
            ItemType itemType,
            ItemStatus itemStatus
    ) {
        return new Item(
                itemId,
                1,
                itemType,
                "existing item",
                "ETC",
                "existing item description",
                "test location",
                LocalDateTime.of(2026, 10, 17, 10, 0),
                itemStatus,
                null
        );
    }
}
