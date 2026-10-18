package com.polink.investigation.service;

import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.CaseExistenceRepository;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB에 연결하지 않고 Service의 처리 로직 검사
@ExtendWith(MockitoExtension.class)
class WitnessServiceTest {

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private CaseExistenceRepository caseExistenceRepository;

    // 가짜 Repository들을 연결해 테스트할 Service 생성
    @InjectMocks
    private WitnessService witnessService;

    private static final String CASE_ID = "SMPA-2026-0405";

    // 정상적인 목격자 및 최초 진술 등록 요청
    private final WitnessCreateRequest request =
            new WitnessCreateRequest(
                    "테스트 목격자",
                    "010-1234-5678",
                    "인근 상점 직원",
                    "오후 9시경 현장 앞을 지나가는 사람을 봤습니다."
            );

    // 1. 첫 번호 생성, 목격자·최초 진술 저장 및 응답 변환 검사
    @Test
    @DisplayName("첫 목격자는 01번으로 등록되고 최초 진술이 연결된다")
    void createFirstWitness() {

        // 준비: 사건은 존재하고 등록된 목격자는 없는 상황
        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        when(witnessRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of());

        prepareSuccessfulSave();

        // 실행
        WitnessCreateResponse response =
                witnessService.createWitness(CASE_ID, request);

        // 저장 메서드에 전달된 목격자와 진술을 가져와 검사
        ArgumentCaptor<Witness> witnessCaptor =
                ArgumentCaptor.forClass(Witness.class);

        ArgumentCaptor<Statement> statementCaptor =
                ArgumentCaptor.forClass(Statement.class);

        verify(witnessRepository)
                .saveAndFlush(witnessCaptor.capture());

        verify(statementRepository)
                .save(statementCaptor.capture());

        Witness witness = witnessCaptor.getValue();
        Statement statement = statementCaptor.getValue();

        // 확인: 첫 목격자 번호와 입력 정보
        assertEquals("W-2026-0405-01", witness.getWitnessId());
        assertEquals(CASE_ID, witness.getCaseId());
        assertEquals(request.name(), witness.getName());
        assertEquals(request.phone(), witness.getPhone());
        assertEquals(request.relationship(), witness.getRelationship());
        assertNotNull(witness.getCreatedAt());

        // 확인: 최초 진술이 같은 목격자 번호로 연결되는지 검사
        assertEquals(witness.getWitnessId(), statement.getWitnessId());
        assertEquals(request.content(), statement.getContent());
        assertEquals("INITIAL", statement.getStatementType());

        // 확인: 목격자와 최초 진술에 동일한 등록 일시 사용
        assertEquals(witness.getCreatedAt(), statement.getCreatedAt());

        // 확인: 저장 결과가 응답 DTO에 반영되는지 검사
        assertEquals(witness.getWitnessId(), response.witnessId());
        assertEquals(CASE_ID, response.caseId());
        assertEquals(request.name(), response.name());
        assertEquals(request.phone(), response.phone());
        assertEquals(request.relationship(), response.relationship());
        assertEquals(Integer.valueOf(1), response.statementId());
        assertEquals(request.content(), response.content());
        assertEquals(
                statement.getCreatedAt(),
                response.statementCreatedAt()
        );
    }

    // 2. 정렬되지 않은 목록에서도 최대 순번 다음 번호를 생성하는지 검사
    @Test
    @DisplayName("조회 순서와 관계없이 최대 순번 다음 번호로 등록된다")
    void createNextWitness() {

        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        // 마지막 항목은 01이지만 최대 순번은 03인 상황
        when(witnessRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of(
                        savedWitness("W-2026-0405-03"),
                        savedWitness("W-2026-0405-01")
                ));

        prepareSuccessfulSave();

        // 실행
        WitnessCreateResponse response =
                witnessService.createWitness(CASE_ID, request);

        // 확인: 최대 순번 03 다음인 04 생성
        assertEquals("W-2026-0405-04", response.witnessId());
    }

    // 3. 사건이 없으면 목격자 조회·저장 및 진술 저장을 중단하는지 검사
    @Test
    @DisplayName("사건이 없으면 404 예외가 발생하고 저장하지 않는다")
    void rejectMissingCase() {

        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(false);

        // 실행 및 확인
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> witnessService.createWitness(CASE_ID, request)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        // 사건 확인에 실패하면 두 Repository 모두 사용하지 않음
        verifyNoInteractions(
                witnessRepository,
                statementRepository
        );
    }

    // 4. 순번이 99이면 다음 번호를 만들거나 저장하지 않는지 검사
    @Test
    @DisplayName("최대 순번이 99이면 목격자와 진술을 저장하지 않는다")
    void rejectSequenceOverflow() {

        when(caseExistenceRepository.existsByCaseId(CASE_ID))
                .thenReturn(true);

        when(witnessRepository.findByCaseId(CASE_ID))
                .thenReturn(List.of(
                        savedWitness("W-2026-0405-99")
                ));

        // 실행 및 확인
        assertThrows(
                IllegalStateException.class,
                () -> witnessService.createWitness(CASE_ID, request)
        );

        verify(witnessRepository, never())
                .saveAndFlush(any(Witness.class));

        verifyNoInteractions(statementRepository);
    }

    // 정상 등록 테스트에서 사용할 가짜 저장 동작 설정
    private void prepareSuccessfulSave() {

        // 전달받은 목격자를 그대로 반환 — 실제 DB 저장은 하지 않음
        when(witnessRepository.saveAndFlush(any(Witness.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // DB가 진술 번호를 생성하는 상황을 테스트에서 재현
        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);

                    // setter 추가 없이 테스트 객체의 진술 번호만 설정
                    ReflectionTestUtils.setField(
                            statement,
                            "statementId",
                            1
                    );

                    return statement;
                });
    }

    // 이미 등록된 목격자 역할의 테스트 객체 생성
    private Witness savedWitness(String witnessId) {
        return new Witness(
                witnessId,
                CASE_ID,
                "기존 목격자",
                "010-1111-2222",
                "인근 주민",
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
    }
}