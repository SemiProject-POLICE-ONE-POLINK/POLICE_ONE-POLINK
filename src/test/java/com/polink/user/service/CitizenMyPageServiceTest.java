package com.polink.user.service;

import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseCloseRepository;
import com.polink.casefile.repository.CaseRepository;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import com.polink.user.dto.response.CitizenMyPageResponse;
import com.polink.user.dto.response.MyCaseSummaryResponse;
import com.polink.user.dto.response.MyItemSummaryResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

// 실제 DB 없이 본인 자료 조회·반환 상태 연결·접근 제한 검사
@ExtendWith(MockitoExtension.class)
class CitizenMyPageServiceTest {

    private static final Integer USER_ID = 1;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 20, 10, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseCloseRepository caseCloseRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    private CitizenMyPageService service;
    private User citizen;

    @BeforeEach
    void setUp() {
        service = new CitizenMyPageService(
                userRepository,
                caseRepository,
                caseCloseRepository,
                itemRepository,
                returnRequestRepository
        );

        citizen = createUser(USER_ID, UserRole.CITIZEN);
    }

    // ==================== 정상 조회 ====================

    @Test
    @DisplayName("본인의 사건·물품과 최신 반환 신청 정보를 응답한다")
    void getCitizenMyPage() {
        prepareLogin();

        CaseFile investigating = createCase(
                "SMPA-2026-0403",
                CaseStatus.INVESTIGATING
        );
        CaseFile closed = createCase(
                "SMPA-2026-0402",
                CaseStatus.CLOSED
        );
        CaseFile canceled = createCase(
                "SMPA-2026-0401",
                CaseStatus.CANCELED
        );

        LocalDateTime closedAt = BASE_TIME.plusDays(1);
        CaseClose closeRecord = createClose(closed, closedAt);

        Item lostWithoutRequest = createItem(
                "L-0002", ItemType.LOST
        );
        Item found = createItem(
                "F-0001", ItemType.FOUND
        );
        Item lostWithRequest = createItem(
                "L-0001", ItemType.LOST
        );

        ReturnRequest newerRequest = createReturnRequest(
                20, "L-0001", ReturnRequestStatus.APPROVED
        );
        ReturnRequest olderRequest = createReturnRequest(
                19, "L-0001", ReturnRequestStatus.REJECTED
        );

        // Repository가 최신순으로 반환하는 결과를 준비
        prepareData(
                List.of(investigating, closed, canceled),
                List.of(closeRecord),
                List.of(lostWithoutRequest, found, lostWithRequest),
                List.of(newerRequest, olderRequest)
        );

        CitizenMyPageResponse response =
                service.getCitizenMyPage(USER_ID);

        assertEquals(3L, response.totalCaseCount());
        assertEquals(3L, response.totalItemCount());

        // Repository가 반환한 사건 순서를 유지
        assertEquals(
                List.of(
                        "SMPA-2026-0403",
                        "SMPA-2026-0402",
                        "SMPA-2026-0401"
                ),
                response.cases().stream()
                        .map(MyCaseSummaryResponse::caseId)
                        .toList()
        );

        // 종결된 사건에만 종결 일시 연결
        assertNull(response.cases().get(0).closedAt());
        assertEquals(closedAt, response.cases().get(1).closedAt());
        assertNull(response.cases().get(2).closedAt());

        MyCaseSummaryResponse canceledResponse =
                response.cases().get(2);

        assertAll(
                () -> assertEquals(
                        "테스트 사건 SMPA-2026-0401",
                        canceledResponse.title()
                ),
                () -> assertEquals(
                        CaseType.THEFT,
                        canceledResponse.caseType()
                ),
                () -> assertEquals(
                        CaseStatus.CANCELED,
                        canceledResponse.status()
                ),
                () -> assertEquals(
                        BASE_TIME,
                        canceledResponse.createdAt()
                ),
                () -> assertEquals(
                        "테스트 장소",
                        canceledResponse.location()
                ),
                () -> assertEquals(
                        "테스트 취소 사유",
                        canceledResponse.cancelReason()
                )
        );

        // 물품 순서와 유형 구분 유지
        assertEquals(
                List.of("L-0002", "F-0001", "L-0001"),
                response.items().stream()
                        .map(MyItemSummaryResponse::itemId)
                        .toList()
        );

        // 반환 신청이 없는 분실물은 신청 정보가 null
        assertNull(response.items().get(0).returnRequestId());
        assertNull(response.items().get(0).returnStatus());

        // 습득물에는 반환 신청 정보를 연결하지 않음
        assertEquals(
                ItemType.FOUND,
                response.items().get(1).itemType()
        );
        assertNull(response.items().get(1).returnRequestId());
        assertNull(response.items().get(1).returnStatus());

        // 같은 분실물의 여러 신청 중 최신 신청을 사용
        assertEquals(
                new MyItemSummaryResponse(
                        "L-0001",
                        "테스트 물품 L-0001",
                        ItemType.LOST,
                        ItemStatus.LOST,
                        "테스트 장소",
                        BASE_TIME.toLocalDate(),
                        20,
                        ReturnRequestStatus.APPROVED
                ),
                response.items().get(2)
        );

        // 로그인한 회원 번호로 본인 자료를 조회하는지 확인
        verify(caseRepository).findMyCasesForMyPage(USER_ID);

        verify(caseCloseRepository).findAllById(
                List.of("SMPA-2026-0402")
        );

        verify(itemRepository)
                .findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                        USER_ID, Pageable.unpaged()
                );

        verify(returnRequestRepository)
                .findByRequesterIdOrderByCreatedAtDesc(
                        USER_ID, Pageable.unpaged()
                );

        // 전체 사건 조회를 사용하지 않아야 함
        verify(caseRepository, never()).findAll();
    }

    @Test
    @DisplayName("종결 기록의 조회 순서가 달라도 사건번호에 맞게 종결일을 연결한다")
    void connectCloseDatesByCaseId() {
        prepareLogin();

        CaseFile firstCase = createCase(
                "SMPA-2026-0402", CaseStatus.CLOSED
        );
        CaseFile secondCase = createCase(
                "SMPA-2026-0401", CaseStatus.CLOSED
        );

        LocalDateTime firstClosedAt = BASE_TIME.plusDays(2);
        LocalDateTime secondClosedAt = BASE_TIME.plusDays(1);

        // 사건 목록과 종결 기록의 순서를 반대로 준비
        prepareData(
                List.of(firstCase, secondCase),
                List.of(
                        createClose(secondCase, secondClosedAt),
                        createClose(firstCase, firstClosedAt)
                ),
                List.of(),
                List.of()
        );

        CitizenMyPageResponse response =
                service.getCitizenMyPage(USER_ID);

        assertEquals(
                firstClosedAt,
                response.cases().get(0).closedAt()
        );
        assertEquals(
                secondClosedAt,
                response.cases().get(1).closedAt()
        );
    }

    @Test
    @DisplayName("종결 사건의 종결 기록이 없으면 종결일을 null로 반환한다")
    void returnNullWhenCloseRecordMissing() {
        prepareLogin();

        CaseFile closed = createCase(
                "SMPA-2026-0401", CaseStatus.CLOSED
        );

        prepareData(
                List.of(closed),
                List.of(),
                List.of(),
                List.of()
        );

        CitizenMyPageResponse response =
                service.getCitizenMyPage(USER_ID);

        assertEquals(1L, response.totalCaseCount());
        assertNull(response.cases().get(0).closedAt());
        assertEquals(0L, response.totalItemCount());
        assertTrue(response.items().isEmpty());
    }

    @Test
    @DisplayName("등록한 사건·물품이 없으면 빈 목록과 0건을 반환한다")
    void returnEmptyMyPage() {
        prepareLogin();
        prepareData(
                List.of(), List.of(), List.of(), List.of()
        );

        CitizenMyPageResponse response =
                service.getCitizenMyPage(USER_ID);

        assertAll(
                () -> assertEquals(0L, response.totalCaseCount()),
                () -> assertTrue(response.cases().isEmpty()),
                () -> assertEquals(0L, response.totalItemCount()),
                () -> assertTrue(response.items().isEmpty())
        );
    }

    // ==================== 인증·권한 오류 ====================

    @Test
    @DisplayName("로그인 회원 번호가 없으면 401이며 DB를 조회하지 않는다")
    void rejectWithoutLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getCitizenMyPage(null)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                caseRepository,
                caseCloseRepository,
                itemRepository,
                returnRequestRepository
        );
    }

    @Test
    @DisplayName("로그인 회원이 없으면 401이며 마이페이지 자료를 조회하지 않는다")
    void rejectMissingUser() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getCitizenMyPage(USER_ID)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                caseRepository,
                caseCloseRepository,
                itemRepository,
                returnRequestRepository
        );
    }

    @ParameterizedTest(name = "{0} 역할 접근 거부")
    @EnumSource(
            value = UserRole.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "CITIZEN"
    )
    @DisplayName("경찰·관리자는 시민 마이페이지를 조회할 수 없다")
    void rejectNonCitizen(UserRole role) {
        ReflectionTestUtils.setField(citizen, "role", role);
        prepareLogin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getCitizenMyPage(USER_ID)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                caseRepository,
                caseCloseRepository,
                itemRepository,
                returnRequestRepository
        );
    }

    // ==================== 테스트 준비 도우미 ====================

    private void prepareLogin() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(citizen));
    }

    private void prepareData(
            List<CaseFile> cases,
            List<CaseClose> closes,
            List<Item> items,
            List<ReturnRequest> requests
    ) {
        when(caseRepository.findMyCasesForMyPage(USER_ID))
                .thenReturn(cases);

        when(caseCloseRepository.findAllById(anyList()))
                .thenReturn(closes);

        when(itemRepository
                .findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                        USER_ID, Pageable.unpaged()
                ))
                .thenReturn(new PageImpl<>(items));

        when(returnRequestRepository
                .findByRequesterIdOrderByCreatedAtDesc(
                        USER_ID, Pageable.unpaged()
                ))
                .thenReturn(new PageImpl<>(requests));
    }

    private User createUser(Integer id, UserRole role) {
        User user = new User(
                "test-user-" + id,
                "test-encoded-password",
                "테스트 회원",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(user, "userId", id);
        ReflectionTestUtils.setField(user, "role", role);

        return user;
    }

    private CaseFile createCase(String caseId, CaseStatus status) {
        CaseFile caseFile = new CaseFile(
                caseId,
                citizen,
                "테스트 사건 " + caseId,
                CaseType.THEFT,
                "테스트 설명",
                BASE_TIME,
                "테스트 장소"
        );

        ReflectionTestUtils.setField(caseFile, "status", status);
        ReflectionTestUtils.setField(
                caseFile, "createdAt", BASE_TIME
        );

        if (status == CaseStatus.CANCELED) {
            ReflectionTestUtils.setField(
                    caseFile, "cancelReason", "테스트 취소 사유"
            );
        }

        return caseFile;
    }

    private CaseClose createClose(
            CaseFile caseFile,
            LocalDateTime closedAt
    ) {
        CaseClose close = new CaseClose(
                caseFile,
                createUser(11, UserRole.ADMIN),
                "테스트 종결 사유"
        );

        // DB 저장 시 설정되는 값을 테스트에서 직접 준비
        ReflectionTestUtils.setField(
                close, "caseId", caseFile.getCaseId()
        );
        ReflectionTestUtils.setField(
                close, "closedAt", closedAt
        );

        return close;
    }

    private Item createItem(String itemId, ItemType type) {
        return new Item(
                itemId,
                USER_ID,
                type,
                "테스트 물품 " + itemId,
                "ETC",
                "테스트 설명",
                "테스트 장소",
                BASE_TIME,
                type == ItemType.LOST
                        ? ItemStatus.LOST
                        : ItemStatus.STORED,
                null
        );
    }

    private ReturnRequest createReturnRequest(
            Integer id,
            String lostItemId,
            ReturnRequestStatus status
    ) {
        ReturnRequest request = new ReturnRequest(
                USER_ID,
                lostItemId,
                "F-0100",
                "테스트 반환 신청"
        );

        ReflectionTestUtils.setField(request, "requestId", id);
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(
                request, "createdAt", BASE_TIME.plusMinutes(id)
        );

        return request;
    }
}