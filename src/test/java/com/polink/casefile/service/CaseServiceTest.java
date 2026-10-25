package com.polink.casefile.service;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.request.CaseOfficerRequest;
import com.polink.casefile.dto.response.*;
import com.polink.casefile.entity.CaseClose;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseCloseRepository;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseCloseRepository caseCloseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseNumberGenerator caseNumberGenerator;

    private CaseService caseService;

    @BeforeEach
    void setUp() {
        caseService = new CaseService(
                caseRepository,
                userRepository,
                caseNumberGenerator,
                caseCloseRepository
        );
    }

    @Test
    void createCase_success() {
        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9101"
        );

        LocalDateTime occurredAt =
                LocalDateTime.of(2026, 10, 18, 10, 0);

        CaseCreateRequest request = new CaseCreateRequest();
        ReflectionTestUtils.setField(request, "title", "지갑 절도 신고");
        ReflectionTestUtils.setField(request, "caseType", CaseType.THEFT);
        ReflectionTestUtils.setField(request, "description", "가상 사건 내용");
        ReflectionTestUtils.setField(request, "occurredAt", occurredAt);
        ReflectionTestUtils.setField(request, "location", "테스트 장소");

        when(userRepository.findById(1))
                .thenReturn(Optional.of(reporter));

        when(caseNumberGenerator.generate())
                .thenReturn("SMPA-2026-0042");

        when(caseRepository.save(any(CaseFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CaseCreateResponse response =
                caseService.createCase(1, request);

        ArgumentCaptor<CaseFile> captor =
                ArgumentCaptor.forClass(CaseFile.class);

        verify(caseRepository).save(captor.capture());

        CaseFile savedCase = captor.getValue();

        assertEquals("SMPA-2026-0042", savedCase.getCaseId());
        assertSame(reporter, savedCase.getReporter());
        assertNull(savedCase.getAssignedOfficer());
        assertEquals(CaseStatus.WAITING, savedCase.getStatus());
        assertEquals("", savedCase.getCancelReason());

        assertEquals("지갑 절도 신고", savedCase.getTitle());
        assertEquals(CaseType.THEFT, savedCase.getCaseType());
        assertEquals("가상 사건 내용", savedCase.getDescription());
        assertEquals(occurredAt, savedCase.getOccurredAt());
        assertEquals("테스트 장소", savedCase.getLocation());

        assertEquals(savedCase.getCaseId(), response.getCaseId());
        assertEquals(CaseStatus.WAITING, response.getStatus());
    }

    @Test
    void createCase_nonCitizen() {
        User officer = new User(
                "police01", "test-hash", "테스트경찰", "010-0000-9102"
        );
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        when(userRepository.findById(2))
                .thenReturn(Optional.of(officer));

        CaseCreateRequest request = new CaseCreateRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.createCase(2, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("사건 접수는 시민만 가능합니다.",
                exception.getReason());

        verifyNoInteractions(caseNumberGenerator);
        verify(caseRepository, never()).save(any(CaseFile.class));
    }

    @Test
    void createCase_missingReporterId() {
        CaseCreateRequest request = new CaseCreateRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.createCase(null, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verifyNoInteractions(
                userRepository, caseNumberGenerator, caseRepository
        );
    }

    @Test
    void createCase_reporterNotFound() {
        when(userRepository.findById(999))
                .thenReturn(Optional.empty());

        CaseCreateRequest request = new CaseCreateRequest();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.createCase(999, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(caseNumberGenerator, caseRepository);
    }

    @Test
    void getCaseDetail_ownCase() {
        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9101"
        );
        ReflectionTestUtils.setField(reporter, "userId", 1);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        when(userRepository.findById(1))
                .thenReturn(Optional.of(reporter));
        when(caseRepository.findById("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        CaseDetailResponse response =
                caseService.getCaseDetail(1, "SMPA-2026-0042");

        assertEquals("SMPA-2026-0042", response.getCaseId());
        assertEquals("지갑 절도 신고", response.getTitle());
        assertEquals(CaseStatus.WAITING, response.getStatus());
        assertNull(response.getAssignedOfficerId());

        verifyNoInteractions(caseNumberGenerator);
        verify(caseRepository, never()).save(any(CaseFile.class));
    }

    @Test
    void getCaseDetail_otherCitizenCase() {
        User reporter = new User(
                "reporter01", "test-hash", "신고자", "010-0000-9201"
        );
        ReflectionTestUtils.setField(reporter, "userId", 1);

        User otherCitizen = new User(
                "citizen02", "test-hash", "다른시민", "010-0000-9202"
        );
        ReflectionTestUtils.setField(otherCitizen, "userId", 2);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        when(userRepository.findById(2))
                .thenReturn(Optional.of(otherCitizen));
        when(caseRepository.findById("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getCaseDetail(2, "SMPA-2026-0042")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(caseNumberGenerator);
        verify(caseRepository, never()).save(any(CaseFile.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"POLICE", "ADMIN"}
    )
    void getCaseDetail_staffCanAccess(UserRole role) {
        User reporter = new User(
                "reporter01", "test-hash", "신고자", "010-0000-9301"
        );
        ReflectionTestUtils.setField(reporter, "userId", 1);

        User staff = new User(
                "staff01", "test-hash", "담당직원", "010-0000-9302"
        );
        ReflectionTestUtils.setField(staff, "userId", 2);
        ReflectionTestUtils.setField(staff, "role", role);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        when(userRepository.findById(2))
                .thenReturn(Optional.of(staff));
        when(caseRepository.findById("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        CaseDetailResponse response =
                caseService.getCaseDetail(2, "SMPA-2026-0042");

        assertEquals("SMPA-2026-0042", response.getCaseId());

        verifyNoInteractions(caseNumberGenerator);
        verify(caseRepository, never()).save(any(CaseFile.class));
    }

    @Test
    void getMyCases_policeOnlyAssignedCases() {
        User officer = new User(
                "police01", "test-hash", "테스트경찰", "010-0000-9401"
        );
        ReflectionTestUtils.setField(officer, "userId", 2);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(2))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findByAssignedOfficer_UserId(2, pageable))
                .thenReturn(Page.empty(pageable));

        MyCaseListResponse response =
                caseService.getMyCases(2, pageable);

        assertTrue(response.getItems().isEmpty());
        assertEquals(0L, response.getTotalElements());

        verify(caseRepository)
                .findByAssignedOfficer_UserId(2, pageable);
        verify(caseRepository, never()).findAll(any(Pageable.class));
        verify(caseRepository, never())
                .findByReporter_UserId(any(), any(Pageable.class));
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void getMyCases_citizenOnlyOwnCases() {
        User citizen = new User(
                "citizen01",
                "test-hash",
                "테스트시민",
                "010-0000-9701"
        );
        ReflectionTestUtils.setField(citizen, "userId", 1);

        LocalDateTime occurredAt =
                LocalDateTime.of(2026, 10, 18, 10, 0);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                citizen,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                occurredAt,
                "테스트 장소"
        );

        Pageable pageable = PageRequest.of(0, 10);
        Page<CaseFile> page =
                new PageImpl<>(List.of(caseFile), pageable, 1);

        when(userRepository.findById(1))
                .thenReturn(Optional.of(citizen));
        when(caseRepository.findByReporter_UserId(1, pageable))
                .thenReturn(page);

        MyCaseListResponse response =
                caseService.getMyCases(1, pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());

        var item = response.getItems().get(0);
        assertEquals("SMPA-2026-0042", item.getCaseId());
        assertEquals("지갑 절도 신고", item.getTitle());
        assertEquals(CaseStatus.WAITING, item.getStatus());

        verify(caseRepository)
                .findByReporter_UserId(1, pageable);
        verify(caseRepository, never())
                .findByAssignedOfficer_UserId(any(), any(Pageable.class));
        verify(caseRepository, never())
                .findAll(any(Pageable.class));
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void getMyCases_adminForbidden() {
        User admin = new User(
                "admin01",
                "test-hash",
                "테스트관리자",
                "010-0000-9702"
        );
        ReflectionTestUtils.setField(admin, "userId", 3);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(3))
                .thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getMyCases(3, pageable)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(
                "내 사건 조회는 시민과 경찰만 가능합니다.",
                exception.getReason()
        );

        verifyNoInteractions(caseRepository, caseNumberGenerator);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"POLICE", "ADMIN"}
    )
    void getCases_staffCanReadAll(UserRole role) {
        User staff = new User(
                "staff01", "test-hash", "테스트직원", "010-0000-9501"
        );
        ReflectionTestUtils.setField(staff, "userId", 2);
        ReflectionTestUtils.setField(staff, "role", role);

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(2))
                .thenReturn(Optional.of(staff));
        when(caseRepository.findAll(pageable))
                .thenReturn(Page.empty(pageable));

        CaseListResponse response =
                caseService.getCases(2, pageable);

        assertTrue(response.getItems().isEmpty());
        assertEquals(0L, response.getTotalElements());

        verify(caseRepository).findAll(pageable);
        verify(caseRepository, never())
                .findByReporter_UserId(any(), any(Pageable.class));
        verify(caseRepository, never())
                .findByAssignedOfficer_UserId(any(), any(Pageable.class));
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void getCases_citizenOnlyOwnCases() {
        User citizen = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9601"
        );
        ReflectionTestUtils.setField(citizen, "userId", 1);

        LocalDateTime occurredAt =
                LocalDateTime.of(2026, 10, 18, 10, 0);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                citizen,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                occurredAt,
                "테스트 장소"
        );

        Pageable pageable = PageRequest.of(0, 10);
        Page<CaseFile> page =
                new PageImpl<>(List.of(caseFile), pageable, 1);

        when(userRepository.findById(1))
                .thenReturn(Optional.of(citizen));
        when(caseRepository.findByReporter_UserId(1, pageable))
                .thenReturn(page);

        CaseListResponse response =
                caseService.getCases(1, pageable);

        assertEquals(1, response.getItems().size());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());

        var item = response.getItems().get(0);
        assertEquals("SMPA-2026-0042", item.getCaseId());
        assertEquals("지갑 절도 신고", item.getTitle());
        assertEquals(CaseType.THEFT, item.getCaseType());
        assertEquals(occurredAt, item.getOccurredAt());
        assertEquals("테스트 장소", item.getLocation());
        assertEquals(CaseStatus.WAITING, item.getStatus());

        verify(caseRepository).findByReporter_UserId(1, pageable);
        verify(caseRepository, never()).findAll(any(Pageable.class));
        verify(caseRepository, never())
                .findByAssignedOfficer_UserId(any(), any(Pageable.class));
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void assignOfficer_firstAssignment_success() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9701"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User officer = new User(
                "police01", "test-hash", "테스트경찰", "010-0000-9702"
        );
        ReflectionTestUtils.setField(officer, "userId", 20);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9703"
        );
        ReflectionTestUtils.setField(reporter, "userId", 30);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        CaseOfficerRequest request = new CaseOfficerRequest();
        ReflectionTestUtils.setField(request, "officerId", 20);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(userRepository.findById(20))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        CaseOfficerResponse response = caseService.assignOfficer(
                10, "SMPA-2026-0042", request
        );

        assertEquals("SMPA-2026-0042", response.getCaseId());
        assertEquals(20, response.getAssignedOfficerId());
        assertEquals(CaseStatus.INVESTIGATING, response.getStatus());
        assertSame(officer, caseFile.getAssignedOfficer());

        verify(caseRepository)
                .findByCaseIdForUpdate("SMPA-2026-0042");
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void assignOfficer_reassignment_success() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9801"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User previousOfficer = new User(
                "police01", "test-hash", "기존경찰", "010-0000-9802"
        );
        ReflectionTestUtils.setField(previousOfficer, "userId", 20);
        ReflectionTestUtils.setField(previousOfficer, "role", UserRole.POLICE);

        User newOfficer = new User(
                "police02", "test-hash", "새담당경찰", "010-0000-9803"
        );
        ReflectionTestUtils.setField(newOfficer, "userId", 21);
        ReflectionTestUtils.setField(newOfficer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9804"
        );

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        // 기존 경찰이 배정되어 수사 중인 사건을 준비합니다.
        caseFile.assignOfficer(previousOfficer);

        CaseOfficerRequest request = new CaseOfficerRequest();
        ReflectionTestUtils.setField(request, "officerId", 21);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(userRepository.findById(21))
                .thenReturn(Optional.of(newOfficer));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        CaseOfficerResponse response = caseService.assignOfficer(
                10, "SMPA-2026-0042", request
        );

        assertEquals("SMPA-2026-0042", response.getCaseId());
        assertEquals(21, response.getAssignedOfficerId());
        assertEquals(CaseStatus.INVESTIGATING, response.getStatus());

        assertSame(newOfficer, caseFile.getAssignedOfficer());
        assertEquals(CaseStatus.INVESTIGATING, caseFile.getStatus());

        verify(caseRepository)
                .findByCaseIdForUpdate("SMPA-2026-0042");
        verifyNoInteractions(caseNumberGenerator);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "POLICE"}
    )
    void assignOfficer_nonAdmin_forbidden(UserRole role) {
        User requester = new User(
                "requester01", "test-hash", "요청자", "010-0000-9901"
        );
        ReflectionTestUtils.setField(requester, "userId", 10);
        ReflectionTestUtils.setField(requester, "role", role);

        CaseOfficerRequest request = new CaseOfficerRequest();
        ReflectionTestUtils.setField(request, "officerId", 20);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(requester));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.assignOfficer(
                        10, "SMPA-2026-0042", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("담당 경찰 배정은 관리자만 가능합니다.",
                exception.getReason());

        verify(userRepository, never()).findById(20);
        verifyNoInteractions(caseRepository, caseNumberGenerator);
    }

    @Test
    void assignOfficer_sameOfficer_conflict() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9911"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User officer = new User(
                "police01", "test-hash", "담당경찰", "010-0000-9912"
        );
        ReflectionTestUtils.setField(officer, "userId", 20);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "신고자", "010-0000-9913"
        );

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );
        caseFile.assignOfficer(officer);

        CaseOfficerRequest request = new CaseOfficerRequest();
        ReflectionTestUtils.setField(request, "officerId", 20);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(userRepository.findById(20))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.assignOfficer(
                        10, "SMPA-2026-0042", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("현재 담당 경찰과 동일한 경찰입니다.",
                exception.getReason());

        assertSame(officer, caseFile.getAssignedOfficer());
        assertEquals(CaseStatus.INVESTIGATING, caseFile.getStatus());
        verifyNoInteractions(caseNumberGenerator);
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {"INVESTIGATION_DONE", "CLOSED", "CANCELED"}
    )
    void assignOfficer_nonAssignableStatus_conflict(CaseStatus status) {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9921"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User officer = new User(
                "police01", "test-hash", "배정대상경찰", "010-0000-9922"
        );
        ReflectionTestUtils.setField(officer, "userId", 20);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "신고자", "010-0000-9923"
        );

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );
        ReflectionTestUtils.setField(caseFile, "status", status);

        CaseOfficerRequest request = new CaseOfficerRequest();
        ReflectionTestUtils.setField(request, "officerId", 20);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(userRepository.findById(20))
                .thenReturn(Optional.of(officer));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.assignOfficer(
                        10, "SMPA-2026-0042", request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(
                "종결 대기, 종결 또는 취소된 사건은 담당 경찰을 변경할 수 없습니다.",
                exception.getReason()
        );

        assertEquals(status, caseFile.getStatus());
        assertNull(caseFile.getAssignedOfficer());
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void getCaseDetail_caseNotFound() {
        User citizen = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9931"
        );
        ReflectionTestUtils.setField(citizen, "userId", 1);

        when(userRepository.findById(1))
                .thenReturn(Optional.of(citizen));
        when(caseRepository.findById("SMPA-2026-9999"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getCaseDetail(1, "SMPA-2026-9999")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("사건을 찾을 수 없습니다.",
                exception.getReason());

        verify(caseRepository).findById("SMPA-2026-9999");
        verify(caseRepository, never()).save(any(CaseFile.class));
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void caseQueries_missingUserId_unauthorized() {
        Pageable pageable = PageRequest.of(0, 10);

        ResponseStatusException detailException = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getCaseDetail(null, "SMPA-2026-0042")
        );

        ResponseStatusException listException = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getCases(null, pageable)
        );

        ResponseStatusException myCasesException = assertThrows(
                ResponseStatusException.class,
                () -> caseService.getMyCases(null, pageable)
        );

        assertEquals(HttpStatus.UNAUTHORIZED,
                detailException.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                listException.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                myCasesException.getStatusCode());

        verifyNoInteractions(
                userRepository, caseRepository, caseNumberGenerator
        );
    }

    @Test
    void getCases_citizenWithoutCases_emptyList() {
        User citizen = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9941"
        );
        ReflectionTestUtils.setField(citizen, "userId", 1);

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findById(1))
                .thenReturn(Optional.of(citizen));
        when(caseRepository.findByReporter_UserId(1, pageable))
                .thenReturn(Page.empty(pageable));

        CaseListResponse response = caseService.getCases(1, pageable);

        assertTrue(response.getItems().isEmpty());
        assertEquals(0L, response.getTotalElements());

        verify(caseRepository).findByReporter_UserId(1, pageable);
        verify(caseRepository, never()).findAll(any(Pageable.class));
        verify(caseRepository, never())
                .findByAssignedOfficer_UserId(any(), any(Pageable.class));

        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void closeCase_success() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9951"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User officer = new User(
                "police01", "test-hash", "테스트경찰", "010-0000-9952"
        );
        ReflectionTestUtils.setField(officer, "userId", 20);
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9953"
        );
        ReflectionTestUtils.setField(reporter, "userId", 30);

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );

        // 담당 경찰이 수사 종결을 요청한 종결 대기 사건을 준비한다.
        caseFile.assignOfficer(officer);
        caseFile.finishInvestigation();

        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request,
                "closeReason",
                "피의자 검거 및 사건 처리 완료"
        );

        LocalDateTime closedAt =
                LocalDateTime.of(2026, 10, 20, 15, 0);

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));
        when(caseCloseRepository.existsById("SMPA-2026-0042"))
                .thenReturn(false);

        when(caseCloseRepository.save(any(CaseClose.class)))
                .thenAnswer(invocation -> {
                    CaseClose savedCaseClose = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            savedCaseClose,
                            "caseId",
                            caseFile.getCaseId()
                    );
                    ReflectionTestUtils.setField(
                            savedCaseClose,
                            "closedAt",
                            closedAt
                    );

                    return savedCaseClose;
                });

        CaseCloseResponse response = caseService.closeCase(
                10,
                "SMPA-2026-0042",
                request
        );

        assertEquals("SMPA-2026-0042", response.getCaseId());
        assertEquals(CaseStatus.CLOSED, response.getStatus());
        assertEquals(closedAt, response.getClosedAt());
        assertEquals(CaseStatus.CLOSED, caseFile.getStatus());

        ArgumentCaptor<CaseClose> caseCloseCaptor =
                ArgumentCaptor.forClass(CaseClose.class);

        verify(caseCloseRepository).save(caseCloseCaptor.capture());

        CaseClose savedCaseClose = caseCloseCaptor.getValue();
        assertSame(caseFile, savedCaseClose.getCaseFile());
        assertSame(admin, savedCaseClose.getClosedBy());
        assertEquals(
                "피의자 검거 및 사건 처리 완료",
                savedCaseClose.getCloseReason()
        );

        verify(caseRepository)
                .findByCaseIdForUpdate("SMPA-2026-0042");
        verify(caseCloseRepository)
                .existsById("SMPA-2026-0042");
        verifyNoInteractions(caseNumberGenerator);
    }

    @Test
    void closeCase_withoutLogin_throwsUnauthorized() {
        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request,
                "closeReason",
                "피의자 검거 및 사건 처리 완료"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        null,
                        "SMPA-2026-0042",
                        request
                )
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals(
                "로그인 사용자 정보가 필요합니다.",
                exception.getReason()
        );

        verifyNoInteractions(
                userRepository,
                caseRepository,
                caseCloseRepository,
                caseNumberGenerator
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "POLICE"}
    )
    void closeCase_nonAdmin_forbidden(UserRole role) {
        User requester = new User(
                "requester01", "test-hash", "요청자", "010-0000-9961"
        );
        ReflectionTestUtils.setField(requester, "userId", 10);
        ReflectionTestUtils.setField(requester, "role", role);

        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request, "closeReason", "사건 처리 완료"
        );

        when(userRepository.findById(10))
                .thenReturn(Optional.of(requester));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        10, "SMPA-2026-0042", request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(
                "사건 종결은 관리자만 가능합니다.",
                exception.getReason()
        );

        verify(userRepository).findById(10);
        verifyNoInteractions(
                caseRepository,
                caseCloseRepository,
                caseNumberGenerator
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {"WAITING", "INVESTIGATING", "CLOSED", "CANCELED"}
    )
    void closeCase_notInvestigationDone_conflict(CaseStatus status) {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9962"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9963"
        );

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );
        ReflectionTestUtils.setField(caseFile, "status", status);

        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request, "closeReason", "사건 처리 완료"
        );

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        10, "SMPA-2026-0042", request
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(
                "종결 대기 상태인 사건만 종결할 수 있습니다.",
                exception.getReason()
        );
        assertEquals(status, caseFile.getStatus());

        verifyNoInteractions(caseCloseRepository, caseNumberGenerator);
    }

    @Test
    void closeCase_caseNotFound_throwsNotFound() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9964"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request, "closeReason", "사건 처리 완료"
        );

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-9999"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        10, "SMPA-2026-9999", request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("사건을 찾을 수 없습니다.", exception.getReason());

        verifyNoInteractions(caseCloseRepository, caseNumberGenerator);
    }

    @Test
    void closeCase_existingCloseRecord_conflict() {
        User admin = new User(
                "admin01", "test-hash", "테스트관리자", "010-0000-9965"
        );
        ReflectionTestUtils.setField(admin, "userId", 10);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User officer = new User(
                "police01", "test-hash", "테스트경찰", "010-0000-9966"
        );
        ReflectionTestUtils.setField(officer, "role", UserRole.POLICE);

        User reporter = new User(
                "citizen01", "test-hash", "테스트시민", "010-0000-9967"
        );

        CaseFile caseFile = new CaseFile(
                "SMPA-2026-0042",
                reporter,
                "지갑 절도 신고",
                CaseType.THEFT,
                "가상 사건 내용",
                LocalDateTime.of(2026, 10, 18, 10, 0),
                "테스트 장소"
        );
        caseFile.assignOfficer(officer);
        caseFile.finishInvestigation();

        CaseCloseRequest request = new CaseCloseRequest();
        ReflectionTestUtils.setField(
                request, "closeReason", "사건 처리 완료"
        );

        when(userRepository.findById(10))
                .thenReturn(Optional.of(admin));
        when(caseRepository.findByCaseIdForUpdate("SMPA-2026-0042"))
                .thenReturn(Optional.of(caseFile));
        when(caseCloseRepository.existsById("SMPA-2026-0042"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        10, "SMPA-2026-0042", request
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(
                "이미 종결 기록이 존재합니다.",
                exception.getReason()
        );
        assertEquals(
                CaseStatus.INVESTIGATION_DONE,
                caseFile.getStatus()
        );

        verify(caseCloseRepository, never()).save(any(CaseClose.class));
        verifyNoInteractions(caseNumberGenerator);
    }
}