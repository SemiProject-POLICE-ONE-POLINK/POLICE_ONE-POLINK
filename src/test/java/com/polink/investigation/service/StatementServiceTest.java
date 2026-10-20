package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
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

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String WITNESS_ID = "W-2026-0405-01";

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private StatementService statementService;

    private final StatementCreateRequest request =
            new StatementCreateRequest(
                    StatementType.ADDITIONAL,
                    "당시 검은색 옷을 입고 있었습니다."
            );

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

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(savedWitness(CASE_ID)));
        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);
                    assertNull(statement.getStatementId());
                    ReflectionTestUtils.setField(
                            statement,
                            "statementId",
                            10
                    );
                    return statement;
                });

        StatementResponse response =
                statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        typedRequest
                );

        ArgumentCaptor<Statement> captor =
                ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).save(captor.capture());

        Statement statement = captor.getValue();
        assertEquals(WITNESS_ID, statement.getWitnessId());
        assertEquals(statementType, statement.getStatementType());
        assertEquals(typedRequest.content(), statement.getContent());
        assertNotNull(statement.getCreatedAt());

        assertEquals(Integer.valueOf(10), response.statementId());
        assertEquals(statementType, response.statementType());
        assertEquals(typedRequest.content(), response.content());
        assertEquals(statement.getCreatedAt(), response.createdAt());
        verifyNoMoreInteractions(statementRepository);
    }

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
        verifyNoInteractions(witnessRepository, statementRepository);
    }

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

    @Test
    @DisplayName("다른 사건의 목격자이면 404 예외가 발생한다")
    void rejectWitnessFromAnotherCase() {
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
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

    private Witness savedWitness(String caseId) {
        return new Witness(
                WITNESS_ID,
                savedCase(caseId),
                "테스트 목격자",
                "010-1234-5678",
                "인근 주민",
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
    }

    private CaseFile savedCase(String caseId) {
        return new CaseFile(
                caseId,
                null,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                LocalDateTime.of(2026, 10, 17, 9, 0),
                "테스트 장소"
        );
    }
}