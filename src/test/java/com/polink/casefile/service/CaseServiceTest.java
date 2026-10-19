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
}