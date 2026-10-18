package com.polink.casefile.service;

import com.polink.user.entity.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.response.CaseCreateResponse;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.polink.casefile.dto.response.CaseDetailResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.polink.casefile.dto.response.CaseListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import java.util.List;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

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
                caseNumberGenerator
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

        CaseListResponse response =
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

        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getItems().size());

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
}