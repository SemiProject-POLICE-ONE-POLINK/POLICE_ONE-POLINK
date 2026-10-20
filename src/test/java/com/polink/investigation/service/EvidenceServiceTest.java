package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;

// 실제 DB·파일 저장 없이 증거물 Service 로직 검사
@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;
    private static final String FILE_PATH = "test-uploads/scene.jpg";

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private EvidenceFileService evidenceFileService;

    // 권한 검사의 결과는 가짜 객체로 설정
    @Mock
    private InvestigationAccessService investigationAccessService;

    @InjectMocks
    private EvidenceService evidenceService;

    private final EvidenceCreateRequest request =
            new EvidenceCreateRequest(
                    "현장 사진",
                    EvidenceType.PHOTO,
                    "현장 출입구 사진",
                    "현장 출입구"
            );

    @BeforeEach
    void setUpSynchronization() {
        // 파일 정리 callback을 등록할 수 있도록 준비
        // 실제 DB 트랜잭션을 시작하는 것은 아님
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        // 다음 테스트에 callback이 남지 않도록 정리
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ==================== 증거물 등록 테스트 ====================

    @Test
    @DisplayName("첫 증거물은 01번으로 등록되고 응답 DTO를 반환한다")
    void createFirstEvidence() {

        prepareRegistration();

        EvidenceCreateResponse response =
                evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        assertEquals("EV-2026-0405-01", response.evidenceId());
        assertEquals(CASE_ID, response.caseId());
        assertEquals(request.evidenceName(), response.evidenceName());
        assertEquals(request.evidenceType(), response.evidenceType());
        assertEquals(request.description(), response.description());
        assertEquals(
                request.acquiredLocation(),
                response.acquiredLocation()
        );
        assertNotNull(response.acquiredAt());
        assertFalse(response.fileAttached());

        // 등록에는 담당자·수사중 상태 검사를 사용
        verify(investigationAccessService)
                .findInvestigatingCase(CASE_ID, OFFICER_ID);

        verify(evidenceRepository).save(any(Evidence.class));

        // 파일 없는 요청은 파일 저장을 호출하지 않음
        verifyNoInteractions(evidenceFileService);
    }

    @Test
    @DisplayName("조회 순서와 관계없이 최대 순번 다음 번호로 등록한다")
    void createNextEvidence() {

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-03"),
                        savedEvidence("EV-2026-0405-01")
                ));

        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvidenceCreateResponse response =
                evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        assertEquals("EV-2026-0405-04", response.evidenceId());
    }

    @ParameterizedTest(name = "등록 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404, 409})
    @DisplayName("접근 검사에 실패하면 증거물 조회·저장과 파일 처리를 중단한다")
    void rejectRegistrationWhenAccessFails(int status) {

        // 로그인·권한·사건 존재·상태 검사 실패 상황을 각각 재현
        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request,
                        attachedFile()
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(
                evidenceRepository,
                evidenceFileService
        );
    }

    @Test
    @DisplayName("최대 순번이 99이면 저장하지 않는다")
    void rejectSequenceOverflow() {

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-99")
                ));

        assertThrows(
                IllegalStateException.class,
                () -> evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request
                )
        );

        verify(evidenceRepository, never())
                .save(any(Evidence.class));

        verifyNoInteractions(evidenceFileService);
    }

    // ==================== 증거물 목록 조회 테스트 ====================

    @Test
    @DisplayName("증거물을 번호 오름차순으로 정렬하고 응답 DTO로 변환한다")
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

        firstEvidence.setFilePath(FILE_PATH);

        Evidence secondEvidence =
                savedEvidence("EV-2026-0405-02");

        Evidence thirdEvidence =
                savedEvidence("EV-2026-0405-03");

        when(investigationAccessService
                .findAssignedCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        thirdEvidence,
                        firstEvidence,
                        secondEvidence
                ));

        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(
                        CASE_ID,
                        OFFICER_ID
                );

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

        // 조회는 담당자 검사만 사용
        verify(investigationAccessService)
                .findAssignedCase(CASE_ID, OFFICER_ID);

        verify(investigationAccessService, never())
                .findInvestigatingCase(anyString(), anyInt());

        verify(evidenceRepository, never())
                .save(any(Evidence.class));
    }

    @Test
    @DisplayName("등록된 증거물이 없으면 빈 목록을 반환한다")
    void findEmptyEvidenceList() {

        when(investigationAccessService
                .findAssignedCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(
                        CASE_ID,
                        OFFICER_ID
                );

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(evidenceRepository, never())
                .save(any(Evidence.class));
    }

    @ParameterizedTest(name = "조회 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404})
    @DisplayName("접근 검사에 실패하면 증거물 목록을 조회하지 않는다")
    void rejectListWhenAccessFails(int status) {

        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findAssignedCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> evidenceService.findEvidenceList(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(
                evidenceRepository,
                evidenceFileService
        );
    }

    // ==================== 첨부파일 등록·정리 테스트 ====================

    @Test
    @DisplayName("첨부파일 경로를 증거물에 설정하고 첨부 여부를 반환한다")
    void createEvidenceWithFile() {

        MockMultipartFile file = prepareFileRegistration();

        EvidenceCreateResponse response =
                evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request,
                        file
                );

        ArgumentCaptor<Evidence> captor =
                ArgumentCaptor.forClass(Evidence.class);

        verify(evidenceRepository).save(captor.capture());

        // DB 저장 요청에 파일 경로가 포함됐는지 확인
        assertEquals(FILE_PATH, captor.getValue().getFilePath());
        assertTrue(response.fileAttached());

        verify(evidenceFileService).save(file);

        // 종료 결과를 받기 전에는 파일을 삭제하지 않음
        verify(evidenceFileService, never()).delete(anyString());

        // 파일 정리 callback이 등록됐는지 확인
        assertEquals(
                1,
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .size()
        );
    }

    @ParameterizedTest(name = "트랜잭션 종료 상태: {0}")
    @ValueSource(ints = {STATUS_COMMITTED, STATUS_ROLLED_BACK})
    @DisplayName("롤백 결과에는 파일을 삭제하고 커밋 결과에는 유지한다")
    void cleanupAccordingToTransactionResult(int status) {

        MockMultipartFile file = prepareFileRegistration();

        evidenceService.createEvidence(
                CASE_ID,
                OFFICER_ID,
                request,
                file
        );

        // 실제 DB 대신 트랜잭션 종료 결과를 callback에 전달
        completeTransaction(status);

        if (status == STATUS_ROLLED_BACK) {
            verify(evidenceFileService).delete(FILE_PATH);
        } else {
            verify(evidenceFileService, never()).delete(anyString());
        }
    }

    @Test
    @DisplayName("DB 저장 요청 실패 후 롤백 결과를 받으면 파일을 삭제한다")
    void cleanupFileAfterSaveFailure() {

        MockMultipartFile file = attachedFile();

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        when(evidenceFileService.save(file))
                .thenReturn(FILE_PATH);

        IllegalStateException saveException =
                new IllegalStateException("테스트용 DB 저장 실패");

        when(evidenceRepository.save(any(Evidence.class)))
                .thenThrow(saveException);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request,
                        file
                )
        );

        assertSame(saveException, exception);

        // 저장 예외 발생만으로 callback이 자동 실행되는 것은 아님
        verify(evidenceFileService, never()).delete(anyString());

        // 롤백 종료 통지를 받는 상황을 별도로 재현
        completeTransaction(STATUS_ROLLED_BACK);

        verify(evidenceFileService).delete(FILE_PATH);
    }

    @Test
    @DisplayName("파일 검증·저장에 실패하면 증거물을 저장하지 않는다")
    void rejectFileFailure() {

        MockMultipartFile file = attachedFile();

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        IllegalArgumentException fileException =
                new IllegalArgumentException(
                        "지원하지 않는 파일 형식입니다."
                );

        when(evidenceFileService.save(file))
                .thenThrow(fileException);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> evidenceService.createEvidence(
                        CASE_ID,
                        OFFICER_ID,
                        request,
                        file
                )
        );

        assertSame(fileException, exception);

        verify(evidenceRepository, never())
                .save(any(Evidence.class));

        // 파일 경로를 받지 못했으므로 종료 callback도 등록되지 않음
        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    // ==================== 테스트 준비·확인용 메서드 ====================

    // 접근 검사 통과·첫 등록·정상 저장 상황 설정
    private void prepareRegistration() {

        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(evidenceRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 정상적인 첨부파일 등록 상황 설정
    private MockMultipartFile prepareFileRegistration() {

        prepareRegistration();

        MockMultipartFile file = attachedFile();

        when(evidenceFileService.save(file))
                .thenReturn(FILE_PATH);

        return file;
    }

    // 실제 파일을 생성하지 않는 업로드 요청용 테스트 객체
    private MockMultipartFile attachedFile() {
        return new MockMultipartFile(
                "file",
                "scene.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );
    }

    // 등록된 callback에 종료 상태를 전달
    // 실제 트랜잭션 commit·rollback을 실행하는 것은 아님
    private void completeTransaction(int status) {

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        for (TransactionSynchronization synchronization
                : synchronizations) {
            synchronization.afterCompletion(status);
        }

        TransactionSynchronizationManager.clearSynchronization();
    }

    // 응답 변환에 필요한 사건 정보만 가진 테스트 객체
    // 권한·상태 판단은 이 테스트에서 가짜 AccessService로 대체
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

    // 기존에 저장된 증거물 역할의 테스트 객체
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