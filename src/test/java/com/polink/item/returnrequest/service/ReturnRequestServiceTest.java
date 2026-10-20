package com.polink.item.returnrequest.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.dto.ReturnRequestCreateRequest;
import com.polink.item.returnrequest.dto.ReturnRequestCreateResponse;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import com.polink.item.returnrequest.dto.MyReturnRequestListResponse;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.mockito.ArgumentMatchers.isNull;
import com.polink.item.returnrequest.dto.PoliceReturnRequestListResponse;
import com.polink.item.returnrequest.dto.ReturnRequestDetailResponse;

/*
 * 실제 DB 없이 반환 신청 Service의 업무 규칙을 테스트한다.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceTest {

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ReturnRequestService returnRequestService;

    /*
     * 정상적인 분실물·습득물 조합이면 PENDING 상태로 반환 신청을 저장하는지 확인한다.
     */
    @Test
    @DisplayName("본인이 등록한 분실물과 보관 중인 습득물로 반환 신청한다")
    void createReturnRequest() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest(
                        "L-0001",
                        "F-0001",
                        "내가 분실한 카드지갑입니다."
                );

        Item lostItem = item(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST
        );

        Item foundItem = item(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        // 분실물과 습득물이 DB에 존재한다고 가정한다.
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        // 같은 물품 조합의 기존 반환 신청은 없다고 가정한다.
        when(returnRequestRepository.existsByLostItemIdAndFoundItemId(
                "L-0001",
                "F-0001"
        )).thenReturn(false);

        // 실제 DB 대신 Service가 만든 객체를 그대로 저장 결과로 돌려준다.
        when(returnRequestRepository.save(any(ReturnRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReturnRequestCreateResponse response =
                returnRequestService.createReturnRequest(1, request);

        // 새 반환 신청은 처리 대기(PENDING) 상태여야 한다.
        assertEquals(ReturnRequestStatus.PENDING, response.status());

        // Service가 Repository에 저장 요청을 했는지 확인한다.
        ArgumentCaptor<ReturnRequest> captor =
                ArgumentCaptor.forClass(ReturnRequest.class);

        verify(returnRequestRepository).save(captor.capture());

        ReturnRequest savedReturnRequest = captor.getValue();

        // 저장하려는 반환 신청의 값이 요청값과 일치하는지 확인한다.
        assertEquals(1, savedReturnRequest.getRequesterId());
        assertEquals("L-0001", savedReturnRequest.getLostItemId());
        assertEquals("F-0001", savedReturnRequest.getFoundItemId());
        assertEquals(
                "내가 분실한 카드지갑입니다.",
                savedReturnRequest.getRequestReason()
        );
        assertEquals(
                ReturnRequestStatus.PENDING,
                savedReturnRequest.getStatus()
        );
    }

    /*
     * 테스트에서만 사용할 가짜 분실물 또는 습득물 객체를 만든다.
     */
    private Item item(
            String itemId,
            Integer registrantId,
            ItemType itemType,
            ItemStatus itemStatus
    ) {
        return new Item(
                itemId,
                registrantId,
                itemType,
                "테스트 물품",
                "ETC",
                "테스트용 물품 설명",
                "테스트 장소",
                LocalDateTime.of(2026, 10, 19, 10, 0),
                itemStatus,
                null
        );
    }

    /*
     * 다른 시민이 등록한 분실물로는 반환 신청할 수 없다.
     */
    @Test
    @DisplayName("본인이 등록하지 않은 분실물로 반환 신청하면 실패한다")
    void rejectReturnRequestForAnotherUsersLostItem() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest(
                        "L-0001",
                        "F-0001",
                        "내 물품이라고 주장하는 신청입니다."
                );

        // 분실물 등록자는 2번이고, 신청자는 1번인 상황이다.
        Item lostItem = item(
                "L-0001",
                2,
                ItemType.LOST,
                ItemStatus.LOST
        );

        Item foundItem = item(
                "F-0001",
                3,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.createReturnRequest(1, request)
        );

        assertEquals(
                "본인이 등록한 분실물만 반환 신청할 수 있습니다.",
                exception.getMessage()
        );

        // 검증에 실패했으므로 DB 저장은 절대 실행되면 안 된다.
        verify(returnRequestRepository, never())
                .save(any(ReturnRequest.class));
    }

    /*
     * 같은 분실물·습득물 조합으로 이미 신청한 경우 다시 신청할 수 없다.
     */
    @Test
    @DisplayName("같은 물품 조합으로 이미 반환 신청했으면 실패한다")
    void rejectDuplicateReturnRequest() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest(
                        "L-0001",
                        "F-0001",
                        "중복 신청입니다."
                );

        Item lostItem = item(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST
        );

        Item foundItem = item(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        // 이미 같은 조합의 반환 신청이 존재한다고 설정한다.
        when(returnRequestRepository.existsByLostItemIdAndFoundItemId(
                "L-0001",
                "F-0001"
        )).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.createReturnRequest(1, request)
        );

        assertEquals(
                "이미 반환 신청한 물품 조합입니다.",
                exception.getMessage()
        );

        verify(returnRequestRepository, never())
                .save(any(ReturnRequest.class));
    }

    /*
     * 분실물 번호에 습득물(FOUND)을 넣으면 반환 신청할 수 없다.
     */
    @Test
    @DisplayName("분실물 번호가 LOST 유형이 아니면 반환 신청에 실패한다")
    void rejectReturnRequestWhenLostItemTypeIsWrong() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest(
                        "F-0001",
                        "F-0002",
                        "유형이 잘못된 신청입니다."
                );

        // 첫 번째 물품이 LOST가 아니라 FOUND 유형이다.
        Item wrongLostItem = item(
                "F-0001",
                1,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        Item foundItem = item(
                "F-0002",
                2,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(wrongLostItem));
        when(itemRepository.findById("F-0002"))
                .thenReturn(Optional.of(foundItem));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.createReturnRequest(1, request)
        );

        assertEquals(
                "분실물 번호에는 LOST 유형의 물품이 필요합니다.",
                exception.getMessage()
        );

        verify(returnRequestRepository, never())
                .save(any(ReturnRequest.class));
    }

    /*
     * 이미 반환 처리 중이거나 반환된 습득물은 새 반환 신청에 사용할 수 없다.
     */
    @Test
    @DisplayName("습득물이 STORED 상태가 아니면 반환 신청에 실패한다")
    void rejectReturnRequestWhenFoundItemIsNotStored() {
        ReturnRequestCreateRequest request =
                new ReturnRequestCreateRequest(
                        "L-0001",
                        "F-0001",
                        "이미 처리 중인 습득물에 대한 신청입니다."
                );

        Item lostItem = item(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST
        );

        // FOUND 유형이지만, 보관 중(STORED)이 아닌 상태다.
        Item foundItem = item(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.HANDOVER_PENDING
        );

        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.createReturnRequest(1, request)
        );

        assertEquals(
                "반환 가능한 상태의 습득물이 아닙니다.",
                exception.getMessage()
        );

        verify(returnRequestRepository, never())
                .save(any(ReturnRequest.class));
    }

    /*
     * 로그인한 시민의 반환 신청 내역만 최신순으로 조회하는지 확인한다.
     */
    @Test
    @DisplayName("내 반환 신청 목록을 페이지 단위로 조회한다")
    void getMyReturnRequests() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "내가 분실한 물품입니다."
        );

        when(returnRequestRepository.findByRequesterIdOrderByCreatedAtDesc(
                eq(1),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(returnRequest),
                PageRequest.of(0, 10),
                1
        ));

        MyReturnRequestListResponse response =
                returnRequestService.getMyReturnRequests(1, 0, 10);

        assertEquals(1, response.items().size());
        assertEquals(
                ReturnRequestStatus.PENDING,
                response.items().get(0).status()
        );

        // 로그인한 시민 번호와 페이지 조건이 Repository에 전달됐는지 확인한다.
        verify(returnRequestRepository)
                .findByRequesterIdOrderByCreatedAtDesc(
                        eq(1),
                        eq(PageRequest.of(0, 10))
                );
    }

    /**
     * 반환 신청 내역이 하나도 없어도 오류 대신 빈 목록을 반환하는지 확인한다.
     */
    @Test
    @DisplayName("내 반환 신청 내역이 없으면 빈 목록을 반환한다")
    void getMyReturnRequestsReturnsEmptyList() {
        when(returnRequestRepository.findByRequesterIdOrderByCreatedAtDesc(
                eq(1),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        ));

        MyReturnRequestListResponse response =
                returnRequestService.getMyReturnRequests(1, 0, 10);

        assertTrue(response.items().isEmpty());
    }

    /*
     * 로그인 사용자 번호와 페이지 조건이 올바르지 않으면 조회하지 못한다.
     */
    @Test
    @DisplayName("내 반환 신청 조회의 사용자 번호와 페이지 조건을 검증한다")
    void rejectInvalidMyReturnRequestConditions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getMyReturnRequests(null, 0, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getMyReturnRequests(1, -1, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getMyReturnRequests(1, 0, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getMyReturnRequests(1, 0, 101)
        );

        // 잘못된 조건이면 반환 신청 Repository를 조회하지 않아야 한다.
        verifyNoInteractions(returnRequestRepository);
    }

    /**
     * 경찰관이 상태와 검색어로 반환 신청 목록을 조회하는지 확인한다.
     */
    @Test
    @DisplayName("경찰관이 상태와 검색어로 반환 신청 목록을 조회한다")
    void getReturnRequests() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "검은색 카드지갑 반환 신청"
        );

        when(returnRequestRepository.findReturnRequestsByConditions(
                eq(ReturnRequestStatus.PENDING),
                eq("카드지갑"),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(returnRequest),
                PageRequest.of(0, 10),
                1
        ));

        PoliceReturnRequestListResponse response =
                returnRequestService.getReturnRequests(
                        ReturnRequestStatus.PENDING,
                        "카드지갑",
                        0,
                        10
                );

        assertEquals(1, response.items().size());
        assertEquals(
                ReturnRequestStatus.PENDING,
                response.items().get(0).status()
        );

        verify(returnRequestRepository)
                .findReturnRequestsByConditions(
                        eq(ReturnRequestStatus.PENDING),
                        eq("카드지갑"),
                        eq(PageRequest.of(0, 10))
                );
    }

    /**
     * 공백 검색어는 검색 조건이 없는 것과 같으므로 null로 전달하는지 확인한다.
     */
    @Test
    @DisplayName("경찰 목록 조회에서 공백 검색어는 검색 조건 없이 처리한다")
    void getReturnRequestsWithBlankKeyword() {
        when(returnRequestRepository.findReturnRequestsByConditions(
                isNull(),
                isNull(),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        ));

        PoliceReturnRequestListResponse response =
                returnRequestService.getReturnRequests(
                        null,
                        "   ",
                        0,
                        10
                );

        assertTrue(response.items().isEmpty());

        verify(returnRequestRepository)
                .findReturnRequestsByConditions(
                        isNull(),
                        isNull(),
                        eq(PageRequest.of(0, 10))
                );
    }

    /**
     * 경찰관이 반환 신청 번호로 상세 정보를 조회하는지 확인한다.
     */
    @Test
    @DisplayName("반환 신청 번호로 상세 정보를 조회한다")
    void getReturnRequestDetail() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "내가 분실한 카드지갑입니다."
        );

        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));

        ReturnRequestDetailResponse response =
                returnRequestService.getReturnRequestDetail(1);

        assertEquals("L-0001", response.lostItemId());
        assertEquals("F-0001", response.foundItemId());
        assertEquals(
                "내가 분실한 카드지갑입니다.",
                response.requestReason()
        );
        assertEquals(ReturnRequestStatus.PENDING, response.status());

        verify(returnRequestRepository).findById(1);
    }

    /**
     * 존재하지 않는 반환 신청 번호를 조회하면 실패하는지 확인한다.
     */
    @Test
    @DisplayName("존재하지 않는 반환 신청 번호로 상세 조회하면 실패한다")
    void rejectReturnRequestDetailWhenNotFound() {
        when(returnRequestRepository.findById(999))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getReturnRequestDetail(999)
        );

        assertEquals(
                "반환 신청을 찾을 수 없습니다.",
                exception.getMessage()
        );
    }

    /**
     * 0 이하의 반환 신청 번호는 조회할 수 없는지 확인한다.
     */
    @Test
    @DisplayName("반환 신청 번호가 0 이하이면 상세 조회에 실패한다")
    void rejectReturnRequestDetailWithInvalidRequestId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.getReturnRequestDetail(0)
        );

        // 번호 검증에서 실패했으므로 Repository를 조회하면 안 된다.
        verifyNoInteractions(returnRequestRepository);
    }

    /*
     * 대기 중인 반환 신청을 승인하면,
     * 신청은 APPROVED, 두 물품은 HANDOVER_PENDING이 되어야 한다.
     */
    @Test
    @DisplayName("대기 중인 반환 신청을 승인하고 두 물품을 인계 대기로 변경한다")
    void approveReturnRequest() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "내가 분실한 카드지갑입니다."
        );

        Item lostItem = item(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.LOST
        );

        Item foundItem = item(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.STORED
        );

        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        // 10번 경찰관이 1번 반환 신청을 승인한다.
        returnRequestService.approveReturnRequest(10, 1);

        assertEquals(
                ReturnRequestStatus.APPROVED,
                returnRequest.getStatus()
        );
        assertEquals(10, returnRequest.getProcessedBy());
        assertNotNull(returnRequest.getProcessedAt());

        assertEquals(
                ItemStatus.HANDOVER_PENDING,
                lostItem.getStatus()
        );
        assertEquals(
                ItemStatus.HANDOVER_PENDING,
                foundItem.getStatus()
        );
    }

    /*
     * 이미 승인·반려·완료된 신청은 다시 승인할 수 없어야 한다.
     */
    @Test
    @DisplayName("대기 상태가 아닌 반환 신청은 승인할 수 없다")
    void rejectApproveWhenReturnRequestIsNotPending() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "이미 처리된 반환 신청입니다."
        );

        // 이미 다른 경찰관이 승인한 상태로 만든다.
        returnRequest.approve(10);

        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.approveReturnRequest(11, 1)
        );

        assertEquals(
                "대기 상태의 반환 신청만 승인할 수 있습니다.",
                exception.getMessage()
        );

        // 신청 상태 검증에서 실패했으므로 물품을 조회하면 안 된다.
        verifyNoInteractions(itemRepository);
    }

    /*
     * 대기 중인 반환 신청을 반려하면,
     * 신청만 REJECTED가 되고 물품 상태는 기존 상태를 유지해야 한다.
     */
    @Test
    @DisplayName("대기 중인 반환 신청을 반려하고 물품 상태는 유지한다")
    void rejectReturnRequest() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "내가 분실한 카드지갑입니다."
        );

        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));

        returnRequestService.rejectReturnRequest(
                10,
                1,
                "  신청자 정보가 일치하지 않습니다.  "
        );

        assertEquals(
                ReturnRequestStatus.REJECTED,
                returnRequest.getStatus()
        );
        assertEquals(10, returnRequest.getProcessedBy());
        assertNotNull(returnRequest.getProcessedAt());
        assertEquals(
                "신청자 정보가 일치하지 않습니다.",
                returnRequest.getRejectReason()
        );

        // 반려는 물품 상태를 변경하지 않으므로 물품 Repository를 조회하지 않는다.
        verifyNoInteractions(itemRepository);
    }

    /**
     * 승인된 반환 신청의 인계를 완료하면,
     * 신청과 두 물품 모두 반환 완료 상태가 되어야 한다.
     */
    @Test
    @DisplayName("승인된 반환 신청의 인계를 완료하고 두 물품을 반환 완료로 변경한다")
    void completeReturnRequest() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "내가 분실한 카드지갑입니다."
        );

        // 승인된 신청만 인계 완료할 수 있다.
        returnRequest.approve(10);

        Item lostItem = item(
                "L-0001",
                1,
                ItemType.LOST,
                ItemStatus.HANDOVER_PENDING
        );

        Item foundItem = item(
                "F-0001",
                2,
                ItemType.FOUND,
                ItemStatus.HANDOVER_PENDING
        );

        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));
        when(itemRepository.findById("L-0001"))
                .thenReturn(Optional.of(lostItem));
        when(itemRepository.findById("F-0001"))
                .thenReturn(Optional.of(foundItem));

        returnRequestService.completeReturnRequest(1);

        assertEquals(
                ReturnRequestStatus.COMPLETED,
                returnRequest.getStatus()
        );

        assertEquals(ItemStatus.RETURNED, lostItem.getStatus());
        assertNotNull(lostItem.getReturnedAt());

        assertEquals(ItemStatus.RETURNED, foundItem.getStatus());
        assertNotNull(foundItem.getReturnedAt());
    }

    /*
     * 승인되지 않은 반환 신청은 실제 인계 완료 처리할 수 없어야 한다.
     */
    @Test
    @DisplayName("승인되지 않은 반환 신청은 인계 완료할 수 없다")
    void rejectCompleteWhenReturnRequestIsNotApproved() {
        ReturnRequest returnRequest = new ReturnRequest(
                1,
                "L-0001",
                "F-0001",
                "아직 승인되지 않은 반환 신청입니다."
        );

        // 새 신청은 PENDING 상태다.
        when(returnRequestRepository.findById(1))
                .thenReturn(Optional.of(returnRequest));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> returnRequestService.completeReturnRequest(1)
        );

        assertEquals(
                "승인된 반환 신청만 인계 완료 처리할 수 있습니다.",
                exception.getMessage()
        );

        // 신청 상태 검증에서 실패했으므로 물품을 조회하면 안 된다.
        verifyNoInteractions(itemRepository);
    }
}
