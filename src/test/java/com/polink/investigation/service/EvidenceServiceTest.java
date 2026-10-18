package com.polink.investigation.service;

import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.dto.response.EvidenceListResponse;
import com.polink.investigation.entity.Evidence;
import com.polink.investigation.entity.EvidenceType;
import com.polink.investigation.repository.CaseExistenceRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB에 연결하지 않고 Service의 처리 로직을 테스트
@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    // 조회·저장 결과를 테스트에서 지정할 수 있는 가짜 Repository
    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private CaseExistenceRepository caseExistenceRepository;

    // 가짜 Repository들을 주입하여 테스트할 Service 생성
    @InjectMocks
    private EvidenceService evidenceService;

    // 테스트에서 사용할 사건번호
    private static final String CASE_ID = "SMPA-2026-0405";

    // 정상적인 증거물 등록 요청 데이터
    private final EvidenceCreateRequest request =
            new EvidenceCreateRequest(
                    "현장 사진",
                    EvidenceType.PHOTO,
                    "현장 출입구 사진",
                    "현장 출입구"
            );

    // 1. 첫 등록 시 01번이 생성되고, 저장 결과가 응답 DTO로 반환되는지 확인
    @Test
    @DisplayName("첫 증거물은 01번으로 등록된다")
    void createFirstEvidence() {

        // 준비: 해당 사건이 존재하도록 설정
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        // 준비: 해당 사건에 저장된 증거물이 없는 상황
        when(evidenceRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of());

        // 준비: save()에 전달된 증거물 객체를 그대로 반환하도록 설정
        // 실제 DB 저장은 실행하지 않음
        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 실행: 증거물 등록 Service 호출
        EvidenceCreateResponse response =
                evidenceService.createEvidence(CASE_ID, request);

        // 확인: 첫 증거번호가 01번으로 생성됐는지 검사
        assertEquals("EV-2026-0405-01", response.evidenceId());

        // 확인: 사건번호와 요청 정보가 응답에 반영됐는지 검사
        assertEquals(CASE_ID, response.caseId());
        assertEquals("현장 사진", response.evidenceName());
        assertEquals(EvidenceType.PHOTO, response.evidenceType());
        assertEquals("현장 출입구 사진", response.description());
        assertEquals("현장 출입구", response.acquiredLocation());

        // 확인: 확보 일시가 자동 설정되고, 첨부파일은 없는지 검사
        assertNotNull(response.acquiredAt());
        assertFalse(response.fileAttached());

        // 확인: Repository의 저장 메서드가 한 번 호출됐는지 검사
        verify(evidenceRepository).save(any(Evidence.class));
    }

    // 2. 목록이 정렬되어 있지 않아도 가장 큰 순번에 1을 더하는지 확인
    @Test
    @DisplayName("조회 순서와 관계없이 가장 큰 순번 다음 번호로 등록된다")
    void createNextEvidence() {

        // 준비: 해당 사건이 존재하도록 설정
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        // 준비: 순번이 03, 01인 증거물을 순서대로 반환
        // 마지막 항목인 01이 아닌, 가장 큰 순번 03을 찾아야 함
        when(evidenceRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-03"),
                        savedEvidence("EV-2026-0405-01")
                ));

        when(evidenceRepository.save(any(Evidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 실행: 기존 증거물이 있는 사건에 새 증거물 등록
        EvidenceCreateResponse response =
                evidenceService.createEvidence(CASE_ID, request);

        // 확인: 가장 큰 순번 03 다음인 04번이 생성됐는지 검사
        assertEquals("EV-2026-0405-04", response.evidenceId());
    }

    // 3. 사건이 없으면 예외가 발생하고, 증거물 조회·저장을 중단하는지 확인
    @Test
    @DisplayName("사건이 없으면 증거물을 조회하거나 저장하지 않는다")
    void rejectMissingCase() {

        // 준비: 해당 사건이 존재하지 않는 상황
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(false);

        // 실행 및 확인: 등록 요청 시 지정한 예외가 발생하는지 검사
        assertThrows(
                IllegalArgumentException.class,
                () -> evidenceService.createEvidence(CASE_ID, request)
        );

        // 확인: 사건 확인 실패 후 증거물 Repository를 사용하지 않았는지 검사
        verifyNoInteractions(evidenceRepository);
    }

    // 4. 두 자리 순번을 모두 사용했다면 100번을 만들지 않고 등록을 중단하는지 확인
    @Test
    @DisplayName("마지막 순번이 99이면 저장하지 않는다")
    void rejectSequenceOverflow() {

        // 준비: 해당 사건이 존재하도록 설정
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        // 준비: 해당 사건의 마지막 증거물 순번이 99인 상황
        when(evidenceRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of(
                        savedEvidence("EV-2026-0405-99")
                ));

        // 실행 및 확인: 다음 증거물 등록 시 예외가 발생하는지 검사
        assertThrows(
                IllegalStateException.class,
                () -> evidenceService.createEvidence(CASE_ID, request)
        );

        // 확인: 번호 생성 실패 후 저장 메서드가 호출되지 않았는지 검사
        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    // 목록 조회 1. 오름차순 정렬 및 응답 DTO 변환 결과 검사
    @Test
    @DisplayName("증거물 목록을 증거번호 오름차순으로 반환한다")
    void findEvidenceListSorted() {

        // 준비: 목록 응답에 필요한 정보를 가진 증거물
        LocalDateTime acquiredAt =
                LocalDateTime.of(2026, 10, 18, 9, 0);

        Evidence firstEvidence = new Evidence(
                "EV-2026-0405-01",
                CASE_ID,
                "현장 사진",
                EvidenceType.PHOTO,
                "현장 출입구 사진",
                acquiredAt,
                "현장 출입구"
        );

        // 실제 파일은 만들지 않고, 첨부파일 경로만 설정
        firstEvidence.setFilePath("test-uploads/scene.jpg");

        Evidence secondEvidence =
                savedEvidence("EV-2026-0405-02");

        Evidence thirdEvidence =
                savedEvidence("EV-2026-0405-03");

        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        // 정렬되지 않은 순서로 반환하여 Service의 정렬 동작 검사
        when(evidenceRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of(
                        thirdEvidence,
                        firstEvidence,
                        secondEvidence
                ));

        // 실행
        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(CASE_ID);

        // 확인: 전체 증거물이 빠짐없이 오름차순으로 반환되는지 검사
        assertEquals(3, responses.size());
        assertEquals("EV-2026-0405-01", responses.get(0).evidenceId());
        assertEquals("EV-2026-0405-02", responses.get(1).evidenceId());
        assertEquals("EV-2026-0405-03", responses.get(2).evidenceId());

        // 확인: 첫 증거물의 정보가 DTO에 올바르게 반영됐는지 검사
        EvidenceListResponse firstResponse = responses.get(0);

        assertEquals("현장 사진", firstResponse.evidenceName());
        assertEquals(EvidenceType.PHOTO, firstResponse.evidenceType());
        assertEquals(acquiredAt, firstResponse.acquiredAt());
        assertEquals("현장 출입구", firstResponse.acquiredLocation());
        assertEquals("현장 출입구 사진", firstResponse.description());

        // 확인: 첨부파일 경로 유무가 응답에 반영됐는지 검사
        assertTrue(firstResponse.fileAttached());
        assertFalse(responses.get(1).fileAttached());

        // 확인: 목록 조회 과정에서 저장 메서드를 호출하지 않았는지 검사
        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    // 목록 조회 2. 사건은 있지만 증거물이 없는 경우 검사
    @Test
    @DisplayName("등록된 증거물이 없으면 빈 목록을 반환한다")
    void findEmptyEvidenceList() {

        // 준비: 사건은 존재하지만 증거물 조회 결과는 없는 상황
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        when(evidenceRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of());

        // 실행
        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(CASE_ID);

        // 확인: 오류가 아닌 빈 목록으로 반환되는지 검사
        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(evidenceRepository, never()).save(any(Evidence.class));
    }

    // 목록 조회 3. 사건이 없으면 404 예외가 발생하고 조회를 중단하는지 검사
    @Test
    @DisplayName("사건이 없으면 404 예외가 발생한다")
    void rejectEvidenceListForMissingCase() {

        // 준비: 해당 사건이 존재하지 않는 상황
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(false);

        // 실행 및 확인: Service에서 지정한 예외가 발생하는지 검사
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> evidenceService.findEvidenceList(CASE_ID)
        );

        // 확인: 예외에 지정된 상태 코드가 404인지 검사
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // 확인: 사건이 없으면 증거물 Repository를 호출하지 않음
        verifyNoInteractions(evidenceRepository);
    }

    // 기존에 저장된 증거물 역할을 할 객체 생성
    // 테스트 데이터이며 실제 DB에는 저장하지 않음
    private Evidence savedEvidence(String evidenceId) {
        return new Evidence(
                evidenceId,
                CASE_ID,
                "기존 증거물",
                EvidenceType.PHOTO,
                "기존 증거물 설명",
                LocalDateTime.of(2026, 10, 17, 10, 0),
                "현장 출입구"
        );
    }

    @BeforeEach
    void setUpSynchronization() {
        // 트랜잭션 종료 처리 작업을 등록할 수 있도록 준비
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        // 다음 테스트에 등록된 작업이 남지 않도록 정리
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}