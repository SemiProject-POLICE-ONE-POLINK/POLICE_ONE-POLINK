package com.polink.investigation.service;

import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.StatementCreateRequest;
import com.polink.investigation.dto.response.StatementResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// 실제 DB에 연결하지 않고 진술 등록 Service의 처리 로직 검사
@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private StatementService statementService;

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String WITNESS_ID = "W-2026-0405-01";

    private final StatementCreateRequest request =
            new StatementCreateRequest(
                    StatementType.ADDITIONAL,
                    "당시 검은색 옷을 입고 있었습니다."
            );

    // 정상 등록: 같은 검사를 추가·정정·번복 유형으로 각각 실행
    @ParameterizedTest(name = "{0} 유형 등록")
    @EnumSource(
            value = StatementType.class,
            names = {"ADDITIONAL", "CORRECTION", "REVERSAL"}
    )
    @DisplayName("선택한 유형으로 새 진술을 저장하고 응답한다")
    void createStatement(StatementType statementType) {

        StatementCreateRequest typedRequest =
                new StatementCreateRequest(
                        statementType,
                        "새로 입력한 진술 내용입니다."
                );

        // 준비: 사건과 해당 사건의 목격자가 존재
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(savedWitness(CASE_ID)));

        // 실제 DB 대신 저장 결과를 반환하도록 설정
        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);

                    // 기존 진술 번호가 아닌 새 진술로 저장 요청하는지 확인
                    assertNull(statement.getStatementId());

                    // DB가 새 진술 번호를 생성하는 상황을 재현
                    ReflectionTestUtils.setField(
                            statement,
                            "statementId",
                            10
                    );

                    return statement;
                });

        // 실행
        StatementResponse response =
                statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        typedRequest
                );

        // 저장 요청에 전달된 진술 객체 확인
        ArgumentCaptor<Statement> captor =
                ArgumentCaptor.forClass(Statement.class);

        verify(statementRepository).save(captor.capture());

        Statement statement = captor.getValue();

        // 확인: 선택한 목격자에게 요청한 유형·내용으로 저장
        assertEquals(WITNESS_ID, statement.getWitnessId());
        assertEquals(statementType, statement.getStatementType());
        assertEquals(typedRequest.content(), statement.getContent());
        assertNotNull(statement.getCreatedAt());

        // 확인: 저장 결과가 응답 DTO에 반영되는지 검사
        assertEquals(Integer.valueOf(10), response.statementId());
        assertEquals(statementType, response.statementType());
        assertEquals(typedRequest.content(), response.content());
        assertEquals(statement.getCreatedAt(), response.createdAt());

        // 기존 진술 조회·삭제 등 다른 작업 없이 새 진술 저장만 호출
        verifyNoMoreInteractions(statementRepository);
    }

    // 오류 1. 사건이 없으면 목격자 조회와 진술 저장 중단
    @Test
    @DisplayName("사건이 없으면 404 예외가 발생하고 저장하지 않는다")
    void rejectMissingCase() {

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(
                witnessRepository,
                statementRepository
        );
    }

    // 오류 2. 목격자가 없으면 진술 저장 중단
    @Test
    @DisplayName("목격자가 없으면 404 예외가 발생하고 저장하지 않는다")
    void rejectMissingWitness() {

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    // 오류 3. 다른 사건 소속 목격자에게 진술 등록 거부
    @Test
    @DisplayName("다른 사건의 목격자이면 404 예외가 발생한다")
    void rejectWitnessFromAnotherCase() {

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);

        // 목격자는 존재하지만 DB에 저장된 소속 사건번호가 다른 상황
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(
                        savedWitness("SMPA-2026-0406")
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    // 오류 4. 최초 진술 유형으로 추가 등록 거부
    @Test
    @DisplayName("INITIAL 유형이면 400 예외가 발생하고 저장하지 않는다")
    void rejectInitialStatement() {

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(savedWitness(CASE_ID)));

        StatementCreateRequest initialRequest =
                new StatementCreateRequest(
                        StatementType.INITIAL,
                        "최초 진술 유형으로 등록을 시도합니다."
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        initialRequest
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    // DB에서 조회한 목격자 역할의 테스트 객체 생성
    private Witness savedWitness(String caseId) {
        return new Witness(
                WITNESS_ID,
                caseId,
                "테스트 목격자",
                "010-1234-5678",
                "인근 주민",
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
    }
}