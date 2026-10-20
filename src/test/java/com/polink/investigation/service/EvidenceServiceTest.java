package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.dto.response.EvidenceListResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;
import com.polink.investigation.repository.EvidenceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private EvidenceService evidenceService;

    private final EvidenceCreateRequest request = new EvidenceCreateRequest(
            "현장 사진",
            EvidenceType.PHOTO,
            "현장 출입구 사진",
            "현장 출입구"
    );

    @BeforeEach
    void setUpSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("첫 증거물은 01번으로 등록된다")
    void createFirstEvidence() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvidenceCreateResponse response =
                evidenceService.createEvidence(CASE_ID, request);

        assertEquals("EV-2026-0405-01", response.evidenceId());
        assertEquals(CASE_ID, response.caseId());
        assertEquals("현장 사진", response.evidenceName());
        assertEquals(EvidenceType.PHOTO, response.evidenceType());
        assertEquals("현장 출입구 사진", response.description());
        assertEquals("현장 출입구", response.acquiredLocation());
        assertNotNull(response.acquiredAt());
        assertFalse(response.fileAttached());

        verify(evidenceRepository).save(any(Evidence.class));
    }

    @Test
    @DisplayName("조회 순서와 관계없이 가장 큰 순번 다음 번호로 등록된다")
    void createNextEvidence() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-03"),
                        savedEvidence("EV-2026-0405-01")
                ));
        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvidenceCreateResponse response =
                evidenceService.createEvidence(CASE_ID, request);

        assertEquals("EV-2026-0405-04", response.evidenceId());
    }

    @Test
    @DisplayName("사건이 없으면 증거물을 조회하거나 저장하지 않는다")
    void rejectMissingCase() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceService.createEvidence(CASE_ID, request)
        );

        verifyNoInteractions(evidenceRepository);
    }

    @Test
    @DisplayName("마지막 순번이 99이면 저장하지 않는다")
    void rejectSequenceOverflow() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(savedEvidence("EV-2026-0405-99")));

        assertThrows(
                IllegalStateException.class,
                () -> evidenceService.createEvidence(CASE_ID, request)
        );

        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    @Test
    @DisplayName("증거물 목록을 증거번호 오름차순으로 반환한다")
    void findEvidenceListSorted() {
        LocalDateTime acquiredAt =
                LocalDateTime.of(2026, 10, 18, 9, 0);

        Evidence firstEvidence = new Evidence(
                "EV-2026-0405-01",
                savedCase(),
                "현장 사진",
                EvidenceType.PHOTO,
                "현장 출입구 사진",
                acquiredAt,
                "현장 출입구"
        );
        firstEvidence.setFilePath("test-uploads/scene.jpg");

        Evidence secondEvidence =
                savedEvidence("EV-2026-0405-02");
        Evidence thirdEvidence =
                savedEvidence("EV-2026-0405-03");

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        thirdEvidence,
                        firstEvidence,
                        secondEvidence
                ));

        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(CASE_ID);

        assertEquals(3, responses.size());
        assertEquals("EV-2026-0405-01", responses.get(0).evidenceId());
        assertEquals("EV-2026-0405-02", responses.get(1).evidenceId());
        assertEquals("EV-2026-0405-03", responses.get(2).evidenceId());

        EvidenceListResponse firstResponse = responses.get(0);
        assertEquals("현장 사진", firstResponse.evidenceName());
        assertEquals(EvidenceType.PHOTO, firstResponse.evidenceType());
        assertEquals(acquiredAt, firstResponse.acquiredAt());
        assertEquals("현장 출입구", firstResponse.acquiredLocation());
        assertEquals("현장 출입구 사진", firstResponse.description());
        assertTrue(firstResponse.fileAttached());
        assertFalse(responses.get(1).fileAttached());

        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    @Test
    @DisplayName("등록된 증거물이 없으면 빈 목록을 반환한다")
    void findEmptyEvidenceList() {
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(CASE_ID);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    @Test
    @DisplayName("사건이 없으면 증거물 목록 조회에서 404 예외가 발생한다")
    void rejectEvidenceListForMissingCase() {
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> evidenceService.findEvidenceList(CASE_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(evidenceRepository);
    }

    private CaseFile savedCase() {
        return new CaseFile(
                CASE_ID,
                null,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                LocalDateTime.of(2026, 10, 17, 9, 0),
                "테스트 장소"
        );
    }

    private Evidence savedEvidence(String evidenceId) {
        return new Evidence(
                evidenceId,
                savedCase(),
                "기존 증거물",
                EvidenceType.PHOTO,
                "기존 증거물 설명",
                LocalDateTime.of(2026, 10, 17, 10, 0),
                "현장 출입구"
        );
    }
}