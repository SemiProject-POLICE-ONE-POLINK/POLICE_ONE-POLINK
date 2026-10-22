package com.polink.user.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.dto.response.AdminDashboardResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseRepository caseRepository;

    private AdminDashboardService service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(userRepository, caseRepository);

        admin = new User(
                "test-admin",
                "encoded-password",
                "테스트 관리자",
                "010-0000-0000"
        );
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
    }

    @Test
    void 관리자_대시보드의_상태별_유형별_건수를_계산한다() {
        // WAITING 사건 2건 중 담당자가 없는 사건은 1건뿐이다.
        CaseFile unassigned = caseFile(
                "CASE-001", CaseType.THEFT, CaseStatus.WAITING,
                null, "2026-10-20T10:00:00"
        );
        CaseFile assignedWaiting = caseFile(
                "CASE-002", CaseType.THEFT, CaseStatus.WAITING,
                admin, "2026-10-19T10:00:00"
        );
        CaseFile investigating = caseFile(
                "CASE-003", CaseType.FRAUD, CaseStatus.INVESTIGATING,
                admin, "2026-10-18T10:00:00"
        );
        CaseFile closed = caseFile(
                "CASE-004", CaseType.MISSING, CaseStatus.CLOSED,
                admin, "2026-10-17T10:00:00"
        );
        CaseFile canceled = caseFile(
                "CASE-005", CaseType.CYBER, CaseStatus.CANCELED,
                null, "2026-10-16T10:00:00"
        );

        when(userRepository.findById(11)).thenReturn(Optional.of(admin));
        when(caseRepository.findAll()).thenReturn(List.of(
                unassigned, assignedWaiting, investigating, closed, canceled
        ));

        AdminDashboardResponse response = service.getAdminDashboard(11);

        assertEquals("테스트 관리자", response.adminName());
        assertEquals(2, response.waitingCaseCount());
        assertEquals(1, response.investigatingCaseCount());
        assertEquals(1, response.closedCaseCount());
        assertEquals(1, response.canceledCaseCount());

        assertEquals(1, response.unassignedCaseCount());
        assertEquals(
                List.of("CASE-001"),
                response.unassignedCases().stream()
                        .map(AdminDashboardResponse.UnassignedCase::caseId)
                        .toList()
        );

        assertEquals(
                List.of(
                        new AdminDashboardResponse.CaseTypeCount(CaseType.THEFT, 2),
                        new AdminDashboardResponse.CaseTypeCount(CaseType.FRAUD, 1),
                        new AdminDashboardResponse.CaseTypeCount(CaseType.MISSING, 1),
                        new AdminDashboardResponse.CaseTypeCount(CaseType.CYBER, 1),
                        new AdminDashboardResponse.CaseTypeCount(CaseType.ETC, 0)
                ),
                response.caseTypeCounts()
        );
    }

    @Test
    void 배정_대기_사건을_최근_접수순으로_정렬한다() {
        CaseFile older = caseFile(
                "CASE-001", CaseType.THEFT, CaseStatus.WAITING,
                null, "2026-10-19T10:00:00"
        );
        CaseFile newerA = caseFile(
                "CASE-002", CaseType.FRAUD, CaseStatus.WAITING,
                null, "2026-10-20T10:00:00"
        );
        CaseFile newerB = caseFile(
                "CASE-003", CaseType.ETC, CaseStatus.WAITING,
                null, "2026-10-20T10:00:00"
        );

        // 담당자가 없는 수사 중 사건도 배정 대기 목록에는 들어가면 안 된다.
        CaseFile investigating = caseFile(
                "CASE-004", CaseType.CYBER, CaseStatus.INVESTIGATING,
                null, "2026-10-21T10:00:00"
        );

        when(userRepository.findById(11)).thenReturn(Optional.of(admin));
        when(caseRepository.findAll()).thenReturn(List.of(
                older, newerA, investigating, newerB
        ));

        AdminDashboardResponse response = service.getAdminDashboard(11);

        assertEquals(3, response.unassignedCaseCount());
        assertEquals(
                List.of("CASE-003", "CASE-002", "CASE-001"),
                response.unassignedCases().stream()
                        .map(AdminDashboardResponse.UnassignedCase::caseId)
                        .toList()
        );
    }

    @Test
    void 사건이_없으면_모든_건수와_유형별_건수를_0으로_반환한다() {
        when(userRepository.findById(11)).thenReturn(Optional.of(admin));
        when(caseRepository.findAll()).thenReturn(List.of());

        AdminDashboardResponse response = service.getAdminDashboard(11);

        assertEquals(0, response.waitingCaseCount());
        assertEquals(0, response.investigatingCaseCount());
        assertEquals(0, response.closedCaseCount());
        assertEquals(0, response.canceledCaseCount());
        assertEquals(0, response.unassignedCaseCount());
        assertTrue(response.unassignedCases().isEmpty());

        // 사건이 없는 유형도 응답에서 빠지지 않아야 한다.
        assertEquals(CaseType.values().length, response.caseTypeCounts().size());
        assertTrue(response.caseTypeCounts().stream()
                .allMatch(typeCount -> typeCount.count() == 0));
    }

    @Test
    void 로그인_사용자_번호가_없으면_401을_반환한다() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdminDashboard(null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(userRepository, caseRepository);
    }

    @Test
    void 회원_번호가_DB에_없으면_401을_반환한다() {
        when(userRepository.findById(11)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdminDashboard(11)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(caseRepository);
    }

    @Test
    void 시민과_경찰은_관리자_대시보드를_조회할_수_없다() {
        when(userRepository.findById(11)).thenReturn(Optional.of(admin));

        for (UserRole role : List.of(UserRole.CITIZEN, UserRole.POLICE)) {
            ReflectionTestUtils.setField(admin, "role", role);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getAdminDashboard(11)
            );

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        }

        verifyNoInteractions(caseRepository);
    }

    private CaseFile caseFile(
            String caseId,
            CaseType type,
            CaseStatus status,
            User assignedOfficer,
            String createdAt
    ) {
        CaseFile caseFile = new CaseFile(
                caseId,
                admin,
                "테스트 사건 " + caseId,
                type,
                "테스트 설명",
                LocalDateTime.parse(createdAt),
                "테스트 장소"
        );

        // Entity에 테스트용 setter가 없어서 필요한 필드만 설정한다.
        ReflectionTestUtils.setField(caseFile, "status", status);
        ReflectionTestUtils.setField(
                caseFile, "assignedOfficer", assignedOfficer
        );
        ReflectionTestUtils.setField(
                caseFile, "createdAt", LocalDateTime.parse(createdAt)
        );

        return caseFile;
    }
}