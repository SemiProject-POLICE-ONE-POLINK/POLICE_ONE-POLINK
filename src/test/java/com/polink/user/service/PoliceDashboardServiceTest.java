package com.polink.user.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import com.polink.item.returnrequest.entity.ReturnRequest;
import com.polink.item.returnrequest.entity.ReturnRequestStatus;
import com.polink.item.returnrequest.repository.ReturnRequestRepository;
import com.polink.user.dto.response.PoliceDashboardResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 DB 없이 경찰 대시보드의 집계·정렬·접근 제한 검사
@ExtendWith(MockitoExtension.class)
class PoliceDashboardServiceTest {

    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 20, 10, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private ReturnRequestRepository returnRequestRepository;

    @Mock
    private ItemRepository itemRepository;

    private PoliceDashboardService service;
    private User officer;

    @BeforeEach
    void setUp() {
        service = new PoliceDashboardService(
                userRepository,
                caseRepository,
                returnRequestRepository,
                itemRepository
        );

        officer = new User(
                "test-police",
                "test-encoded-password",
                "테스트 경찰",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(
                officer, "userId", OFFICER_ID
        );
        ReflectionTestUtils.setField(
                officer, "role", UserRole.POLICE
        );
    }

    // ==================== 정상 조회·집계·정렬 ====================

    @Test
    @DisplayName("본인 배정 사건의 건수와 정렬 결과 및 공통 집계를 반환한다")
    void getPoliceDashboard() {
        prepareLogin();

        CaseFile older = createCase(
                "SMPA-2026-0401",
                CaseStatus.INVESTIGATING,
                BASE_TIME.minusDays(1)
        );

        CaseFile sameTimeLowerId = createCase(
                "SMPA-2026-0402",
                CaseStatus.CLOSED,
                BASE_TIME
        );

        CaseFile sameTimeHigherId = createCase(
                "SMPA-2026-0403",
                CaseStatus.INVESTIGATING,
                BASE_TIME
        );

        // 일부러 정렬되지 않은 사건 목록을 반환
        when(caseRepository.findByAssignedOfficer_UserId(
                OFFICER_ID,
                Pageable.unpaged()
        )).thenReturn(new PageImpl<>(
                List.of(older, sameTimeLowerId, sameTimeHigherId)
        ));

        // 조회한 한 페이지의 크기가 아니라 전체 건수를 사용해야 함
        prepareSharedCounts(7, 11);

        PoliceDashboardResponse response =
                service.getPoliceDashboard(OFFICER_ID);

        assertAll(
                () -> assertEquals(
                        "테스트 경찰", response.officerName()
                ),
                () -> assertEquals(
                        2L, response.investigatingCaseCount()
                ),
                () -> assertEquals(
                        3L, response.totalAssignedCaseCount()
                ),
                () -> assertEquals(
                        7L, response.pendingReturnRequestCount()
                ),
                () -> assertEquals(
                        11L, response.storedFoundItemCount()
                )
        );

        // 최신 접수순, 접수 시각이 같으면 사건번호 내림차순
        assertEquals(
                List.of(
                        "SMPA-2026-0403",
                        "SMPA-2026-0402",
                        "SMPA-2026-0401"
                ),
                response.assignedCases().stream()
                        .map(PoliceDashboardResponse.AssignedCase::caseId)
                        .toList()
        );

        // 사건 정보가 화면용 DTO에 반영되는지 확인
        assertEquals(
                new PoliceDashboardResponse.AssignedCase(
                        "SMPA-2026-0403",
                        "테스트 사건 SMPA-2026-0403",
                        CaseType.THEFT,
                        CaseStatus.INVESTIGATING
                ),
                response.assignedCases().get(0)
        );

        // 전체 사건 대신 로그인한 경찰의 배정 사건을 조회해야 함
        verify(caseRepository).findByAssignedOfficer_UserId(
                OFFICER_ID,
                Pageable.unpaged()
        );
        verify(caseRepository, never()).findAll();
    }

    @Test
    @DisplayName("배정 사건과 공통 집계 자료가 없으면 빈 목록과 0건을 반환한다")
    void returnEmptyDashboard() {
        prepareLogin();
        prepareEmptyAssignedCases();
        prepareSharedCounts(0, 0);

        PoliceDashboardResponse response =
                service.getPoliceDashboard(OFFICER_ID);

        assertAll(
                () -> assertEquals(
                        0L, response.investigatingCaseCount()
                ),
                () -> assertEquals(
                        0L, response.totalAssignedCaseCount()
                ),
                () -> assertEquals(
                        0L, response.pendingReturnRequestCount()
                ),
                () -> assertEquals(
                        0L, response.storedFoundItemCount()
                ),
                () -> assertTrue(
                        response.assignedCases().isEmpty()
                )
        );
    }

    @Test
    @DisplayName("본인 배정 사건이 없어도 전체 반환 대기·습득물 건수는 표시한다")
    void returnSharedCountsWithoutAssignedCases() {
        prepareLogin();
        prepareEmptyAssignedCases();
        prepareSharedCounts(5, 8);

        PoliceDashboardResponse response =
                service.getPoliceDashboard(OFFICER_ID);

        assertEquals(0L, response.totalAssignedCaseCount());
        assertEquals(0L, response.investigatingCaseCount());
        assertTrue(response.assignedCases().isEmpty());

        // 이 두 집계는 특정 경찰의 사건 유무와 관계없는 전체 건수
        assertEquals(5L, response.pendingReturnRequestCount());
        assertEquals(8L, response.storedFoundItemCount());
    }

    // ==================== 인증·권한 오류 ====================

    @Test
    @DisplayName("로그인 회원 번호가 없으면 401이며 Repository를 호출하지 않는다")
    void rejectWithoutLogin() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPoliceDashboard(null)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                caseRepository,
                returnRequestRepository,
                itemRepository
        );
    }

    @Test
    @DisplayName("로그인 회원이 DB에 없으면 401이며 대시보드 자료를 조회하지 않는다")
    void rejectMissingUser() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPoliceDashboard(OFFICER_ID)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        verify(userRepository).findById(OFFICER_ID);

        verifyNoInteractions(
                caseRepository,
                returnRequestRepository,
                itemRepository
        );
    }

    @ParameterizedTest(name = "{0} 역할 접근 거부")
    @EnumSource(
            value = UserRole.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "POLICE"
    )
    @DisplayName("시민·관리자는 403이며 대시보드 자료를 조회하지 않는다")
    void rejectNonPolice(UserRole role) {
        ReflectionTestUtils.setField(officer, "role", role);
        prepareLogin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getPoliceDashboard(OFFICER_ID)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                caseRepository,
                returnRequestRepository,
                itemRepository
        );
    }

    // ==================== 테스트 준비 도우미 ====================

    private void prepareLogin() {
        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
    }

    private void prepareEmptyAssignedCases() {
        when(caseRepository.findByAssignedOfficer_UserId(
                OFFICER_ID,
                Pageable.unpaged()
        )).thenReturn(new PageImpl<>(List.of()));
    }

    // 실제 물품·반환 자료 대신 페이지의 전체 건수를 설정
    private void prepareSharedCounts(
            long pendingCount,
            long storedCount
    ) {
        PageRequest page = PageRequest.of(0, 1);

        List<ReturnRequest> returnContent = pendingCount == 0
                ? List.of()
                : List.of(mock(ReturnRequest.class));

        when(returnRequestRepository.findReturnRequestsByConditions(
                ReturnRequestStatus.PENDING,
                null,
                page
        )).thenReturn(
                new PageImpl<>(returnContent, page, pendingCount)
        );

        List<Item> itemContent = storedCount == 0
                ? List.of()
                : List.of(mock(Item.class));

        when(itemRepository.findItemsByConditions(
                ItemType.FOUND,
                null,
                null,
                null,
                ItemStatus.STORED,
                null,
                null,
                page
        )).thenReturn(
                new PageImpl<>(itemContent, page, storedCount)
        );
    }

    private CaseFile createCase(
            String caseId,
            CaseStatus status,
            LocalDateTime createdAt
    ) {
        CaseFile caseFile = new CaseFile(
                caseId,
                officer,
                "테스트 사건 " + caseId,
                CaseType.THEFT,
                "테스트 설명",
                createdAt,
                "테스트 장소"
        );

        ReflectionTestUtils.setField(
                caseFile, "assignedOfficer", officer
        );
        ReflectionTestUtils.setField(
                caseFile, "status", status
        );
        ReflectionTestUtils.setField(
                caseFile, "createdAt", createdAt
        );

        return caseFile;
    }
}