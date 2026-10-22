package com.polink.item.service;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.dto.ItemPageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.dto.ItemUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import com.polink.item.dto.ItemStatisticsResponse;

/** 실제 DB에 연결하지 않고 ItemService의 번호·조회 처리 규칙을 테스트한다. */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    // 조회·저장 결과를 테스트에서 정할 수 있는 가짜 Repository
    @Mock
    private ItemRepository itemRepository;

    @SuppressWarnings("unused")
    @Mock
    private ItemImageService itemImageService;

    // 실제 파일을 만들지 않고 이미지 업로드 상황을 흉내 내기 위한 가짜 파일이다.
    @Mock
    private MultipartFile image;

    // 가짜 의존성을 주입한 ItemService 생성
    @InjectMocks
    private ItemService itemService;

    private final ItemCreateRequest lostRequest = new ItemCreateRequest(
            ItemType.LOST,
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

        ItemCreateResponse response = itemService.registerItem(lostRequest, 1, null);

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
                ItemType.FOUND,
                "파란 우산",
                "CLOTHING_GOODS",
                "나무 손잡이가 달린 우산입니다.",
                "청솔역",
                LocalDate.of(2026, 10, 18)
        );

        when(itemRepository.findTopByItemTypeOrderByItemIdDesc(ItemType.FOUND))
                .thenReturn(Optional.of(
                        savedItem("F-0215", ItemType.FOUND, ItemStatus.STORED)
                ));
        when(itemRepository.save(any(Item.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemCreateResponse response = itemService.registerItem(foundRequest, 1, null);

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
                null,
                null,
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
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 10))
        );
    }

    // 4. 장소·발생일 범위를 함께 입력하면 Service가 Repository에 올바른 검색 조건을 전달하는지 검사
    @Test
    @DisplayName("장소와 발생일 범위를 입력하면 해당 검색 조건으로 물품을 조회한다")
    void getItemsWithLocationAndDateRange() {
        Item item = savedItem("L-0002", ItemType.LOST, ItemStatus.LOST);

        // 종료일은 다음 날 00:00 미만으로 변환되어, 선택한 종료일 하루 전체를 포함한다.
        LocalDateTime startDateTime = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime endDateExclusive = LocalDateTime.of(2026, 11, 1, 0, 0);

        when(itemRepository.findItemsByConditions(
                isNull(),
                isNull(),
                isNull(),
                eq("청솔역"),
                isNull(),
                eq(startDateTime),
                eq(endDateExclusive),
                any()
        )).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ItemPageResponse response = itemService.getItems(
                null,
                null,
                null,
                "  청솔역  ",
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                0,
                10
        );

        // 앞뒤 공백이 제거된 장소명과 날짜 범위가 Repository에 전달되어야 한다.
        assertEquals("L-0002", response.items().get(0).itemId());
        verify(itemRepository).findItemsByConditions(
                isNull(),
                isNull(),
                isNull(),
                eq("청솔역"),
                isNull(),
                eq(startDateTime),
                eq(endDateExclusive),
                eq(PageRequest.of(0, 10))
        );
    }

    // 5. 시작일이 종료일보다 늦으면 DB 조회 전에 요청 오류로 차단하는지 검사
    @Test
    @DisplayName("시작일이 종료일보다 늦으면 물품 검색을 거절한다")
    void getItemsRejectsReversedDateRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getItems(
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 10, 31),
                        LocalDate.of(2026, 10, 1),
                        0,
                        10
                )
        );

        assertEquals("시작일은 종료일보다 늦을 수 없습니다.", exception.getMessage());
        verifyNoInteractions(itemRepository);
    }

    // 6. 로그인한 사용자가 등록한 물품 목록만 조회하는지 검사
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

    // 7. 시민이 본인이 등록한 분실물 기본 정보를 수정할 수 있는지 검사
    @Test
    @DisplayName("본인이 등록한 분실물을 수정한다")
    void updateOwnLostItem() {
        // 등록자 ID가 1번인 분실 중 상태의 가짜 물품을 준비한다.
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository가 L-0001을 조회하면 위 가짜 물품을 반환하도록 설정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(item));

        // 수정할 물품 기본 정보를 요청 DTO로 만든다.
        ItemUpdateRequest request = new ItemUpdateRequest(
                "수정된 카드지갑",
                "WALLET_CARD",
                "수정된 물품 설명입니다.",
                "청솔역 2번 출구",
                LocalDate.of(2026, 10, 18)
        );

        // 로그인 사용자 ID=1이 본인 물품 수정을 요청한다.
        ItemDetailResponse response = itemService.updateLostItem(
                "L-0001",
                1,
                request
        );

        // API 응답과 실제 Entity의 값이 모두 변경됐는지 확인한다.
        assertEquals("수정된 카드지갑", response.itemName());
        assertEquals("청솔역 2번 출구", response.location());
        assertEquals("수정된 카드지갑", item.getItemName());
        assertEquals(
                LocalDate.of(2026, 10, 18),
                item.getOccurredAt().toLocalDate()
        );

        // 수정 전 물품 조회가 한 번 실행됐는지 확인한다.
        verify(itemRepository).findById("L-0001");
    }

    // 8. 경찰이 보관 중인 습득물의 기본 정보를 수정할 수 있는지 검사
    @Test
    @DisplayName("경찰이 보관 중인 습득물을 수정한다")
    void updateStoredFoundItem() {
        // 보관 중 상태의 습득물 데이터를 준비한다.
        Item item = savedItem("F-0001", ItemType.FOUND, ItemStatus.STORED);

        // Repository가 F-0001을 조회하면 위 습득물을 반환하도록 설정한다.
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(item));

        // 경찰이 수정할 습득물 기본 정보를 만든다.
        ItemUpdateRequest request = new ItemUpdateRequest(
                "수정된 파란 우산",
                "CLOTHING_GOODS",
                "손잡이가 검은색인 파란 우산입니다.",
                "청솔역 안내데스크",
                LocalDate.of(2026, 10, 19)
        );

        // 경찰 사용자 ID=6이 습득물 수정을 요청한다.
        ItemDetailResponse response = itemService.updateFoundItem(
                "F-0001",
                6,
                request
        );

        // 응답과 실제 Entity의 기본 정보가 변경됐는지 확인한다.
        assertEquals("수정된 파란 우산", response.itemName());
        assertEquals("청솔역 안내데스크", response.location());
        assertEquals("수정된 파란 우산", item.getItemName());

        // 수정 후에도 습득물 유형과 보관 상태는 유지되어야 한다.
        assertEquals(ItemType.FOUND, item.getItemType());
        assertEquals(ItemStatus.STORED, item.getStatus());

        // 수정 전에 대상 습득물을 한 번 조회했는지 확인한다.
        verify(itemRepository).findById("F-0001");
    }

    // 9. 습득물 수정 Service에 분실물 번호를 전달하면 400으로 차단되는지 검사
    @Test
    @DisplayName("습득물 수정 시 분실물은 수정할 수 없다")
    void rejectLostItemWhenUpdatingFoundItem() {
        // 분실 중 상태의 분실물 데이터를 준비한다.
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository가 L-0001을 조회하면 위 분실물을 반환하도록 설정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(item));

        ItemUpdateRequest request = new ItemUpdateRequest(
                "수정 시도 물품",
                "ETC",
                "습득물 수정 API로 분실물을 수정하려는 요청입니다.",
                "청솔역",
                LocalDate.of(2026, 10, 19)
        );

        // 습득물 전용 수정 API에 분실물을 전달하면 예외가 발생해야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> itemService.updateFoundItem("L-0001", 6, request)
        );

        // 물품 유형이 맞지 않는 경우 400 Bad Request여야 한다.
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // 대상 물품 조회까지만 실행되고 수정은 진행되지 않아야 한다.
        verify(itemRepository).findById("L-0001");
    }

    // 10. 시민이 본인 분실물에 이미지 한 장을 첨부할 수 있는지 검사
    @Test
    @DisplayName("본인이 등록한 분실물에 이미지를 첨부한다")
    void attachImageToOwnLostItem() {
        // 이미지가 아직 없는 분실 중 상태의 물품을 준비한다.
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository가 대상 분실물을 반환하도록 설정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(item));

        // 실제 파일 저장 대신, 저장됐다고 가정한 파일 경로를 반환한다.
        when(itemImageService.save(image))
                .thenReturn("C:\\temp\\polink-uploads\\item\\item-test.png");

        // 등록자 ID=1인 시민이 자신의 분실물에 이미지를 첨부한다.
        ItemDetailResponse response = itemService.attachLostItemImage(
                "L-0001",
                1,
                image
        );

        // 응답과 Entity에 저장된 이미지 경로가 같은지 확인한다.
        assertEquals(
                "C:\\temp\\polink-uploads\\item\\item-test.png",
                response.imagePath()
        );
        assertEquals(
                "C:\\temp\\polink-uploads\\item\\item-test.png",
                item.getImagePath()
        );

        // 대상 물품 조회와 이미지 파일 저장이 각각 한 번 실행돼야 한다.
        verify(itemRepository).findById("L-0001");
        verify(itemImageService).save(image);
    }

    // 11. 이미 이미지가 있는 분실물에는 두 번째 이미지를 첨부할 수 없는지 검사
    @Test
    @DisplayName("분실물에는 이미지 한 장만 첨부할 수 있다")
    void rejectSecondImageForLostItem() {
        // 이미 이미지 경로가 저장된 분실물 데이터를 준비한다.
        Item item = new Item(
                "L-0001",
                1,
                ItemType.LOST,
                "기존 카드지갑",
                "WALLET_CARD",
                "기존 이미지가 있는 분실물입니다.",
                "청솔역",
                LocalDateTime.of(2026, 10, 17, 10, 0),
                ItemStatus.LOST,
                "C:\\temp\\polink-uploads\\item\\existing-image.png"
        );

        // Repository가 기존 이미지가 있는 분실물을 반환하도록 설정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(item));

        // 두 번째 이미지 첨부는 400 Bad Request 예외가 발생해야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> itemService.attachLostItemImage("L-0001", 1, image)
        );

        // 요구사항인 “이미지 1개”를 위반했으므로 400이어야 한다.
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // 기존 이미지가 있으면 새 파일을 저장하려고 시도하면 안 된다.
        verify(itemImageService, never()).save(image);
    }

    // 12. 다른 시민은 남의 분실물에 이미지를 첨부할 수 없는지 검사
    @Test
    @DisplayName("다른 시민은 분실물에 이미지를 첨부할 수 없다")
    void rejectImageAttachmentForAnotherUsersLostItem() {
        // 등록자 ID=1인 분실물 데이터를 준비한다.
        Item item = savedItem("L-0001", ItemType.LOST, ItemStatus.LOST);

        // Repository가 대상 분실물을 반환하도록 설정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(item));

        // 다른 시민 ID=2가 이미지 첨부를 시도하면 예외가 발생해야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> itemService.attachLostItemImage("L-0001", 2, image)
        );

        // 본인 물품이 아니므로 403 Forbidden이어야 한다.
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // 권한 검증에서 막혔으므로 실제 파일 저장은 시도하면 안 된다.
        verify(itemImageService, never()).save(image);
    }

    @Test
    @DisplayName("물품 통계는 기간별 유형·상태·카테고리·일별·월별 집계를 함께 반환한다")
    void getItemStatisticsByPeriod() {
        // 테스트 실행일을 기준으로 기간을 정해 날짜 계산이 항상 유효하게 한다.
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();

        Item lostItem = createItem(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST,
                "검은 카드지갑",
                "WALLET_CARD",
                "청솔역"
        );

        Item storedFoundItem = createItem(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.STORED,
                "검은 지갑",
                "WALLET_CARD",
                "청솔역 안내데스크"
        );

        Item returnedFoundItem = createItem(
                "F-0002",
                3,
                ItemType.FOUND,
                ItemStatus.RETURNED,
                "무선 이어폰",
                "ELECTRONICS",
                "청솔역 대합실"
        );

        // Repository가 기간에 포함되는 물품 세 건을 반환하도록 설정한다.
        when(itemRepository.findStatisticsItems(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(
                lostItem,
                storedFoundItem,
                returnedFoundItem
        ));

        ItemStatisticsResponse response = itemService.getItemStatistics(
                startDate,
                endDate
        );

        // 조회 기간과 전체 건수가 응답에 그대로 포함되어야 한다.
        assertEquals(startDate, response.startDate());
        assertEquals(endDate, response.endDate());
        assertEquals(3L, response.totalCount());

        // 분실물 1건, 습득물 2건으로 집계되어야 한다.
        assertEquals(1L, response.lostCount());
        assertEquals(2L, response.foundCount());

        // 반환 완료 1건, 인계 대기 0건을 별도 요약 값으로 반환한다.
        assertEquals(0L, response.handoverPendingCount());
        assertEquals(1L, response.returnedCount());

        // 유형·상태별 Map에도 값이 모두 포함되어야 한다.
        assertEquals(1L, response.itemTypeCounts().get(ItemType.LOST));
        assertEquals(2L, response.itemTypeCounts().get(ItemType.FOUND));
        assertEquals(1L, response.statusCounts().get(ItemStatus.LOST));
        assertEquals(1L, response.statusCounts().get(ItemStatus.STORED));
        assertEquals(0L, response.statusCounts().get(ItemStatus.HANDOVER_PENDING));
        assertEquals(1L, response.statusCounts().get(ItemStatus.RETURNED));

        // 카테고리는 건수 내림차순으로 정렬되어 WALLET_CARD가 먼저 나온다.
        assertEquals(2, response.categoryCounts().size());
        assertEquals("WALLET_CARD", response.categoryCounts().get(0).category());
        assertEquals(2L, response.categoryCounts().get(0).count());

        // 세 물품은 테스트 실행 시점에 생성되므로 같은 일자·월 통계 한 건으로 묶인다.
        assertEquals(1, response.dailyCounts().size());
        assertEquals(3L, response.dailyCounts().get(0).count());
        assertEquals(1, response.monthlyCounts().size());
        assertEquals(3L, response.monthlyCounts().get(0).count());

        // 시작일·종료일이 Repository 조회 조건으로 정확히 전달됐는지 확인한다.
        verify(itemRepository).findStatisticsItems(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
    }

    /*
     * 통계 테스트에서 등록자·유형·상태·카테고리·장소를 직접 지정해
     * 다양한 집계 데이터를 만들기 위한 보조 메서드다.
     */
    private Item createItem(
            String itemId,
            Integer registrantId,
            ItemType itemType,
            ItemStatus itemStatus,
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
                "통계 테스트용 물품 설명입니다.",
                location,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                itemStatus,
                null
        );
    }

    // 시작일이 종료일보다 늦으면 Repository 조회 전 400 대상 예외가 발생해야 한다.
    @Test
    @DisplayName("통계 시작일이 종료일보다 늦으면 예외가 발생한다")
    void rejectsStatisticsWhenStartDateIsAfterEndDate() {
        LocalDate startDate = LocalDate.of(2026, 10, 31);
        LocalDate endDate = LocalDate.of(2026, 10, 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> itemService.getItemStatistics(startDate, endDate)
        );

        assertEquals(
                "통계 시작일은 종료일보다 늦을 수 없습니다.",
                exception.getMessage()
        );

        // 날짜 검증에서 중단되므로 DB 조회는 발생하면 안 된다.
        verifyNoInteractions(itemRepository);
    }

}
