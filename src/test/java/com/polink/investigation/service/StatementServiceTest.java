package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
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
import org.junit.jupiter.params.provider.ValueSource;
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

// 실제 DB 없이 진술 등록과 공통 접근 검사 연결 확인
@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String WITNESS_ID = "W-2026-0405-01";
    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 17, 10, 0);

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private InvestigationAccessService investigationAccessService;

    @InjectMocks
    private StatementService statementService;

    private final StatementCreateRequest request =
            new StatementCreateRequest(
                    StatementType.ADDITIONAL,
                    "당시 검은색 옷을 입고 있었습니다."
            );

    // ==================== 정상 등록 테스트 ====================

    @ParameterizedTest(name = "{0} 유형 등록")
    @EnumSource(
            value = StatementType.class,
            names = {"ADDITIONAL", "CORRECTION", "REVERSAL"}
    )
    @DisplayName("선택한 유형으로 새 진술을 저장하고 응답한다")
    void createStatement(StatementType statementType) {

        prepareAccess();

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(savedWitness(CASE_ID)));

        StatementCreateRequest typedRequest =
                new StatementCreateRequest(
                        statementType,
                        "새로 입력한 진술 내용입니다."
                );

        // 실제 DB 대신 저장 결과를 반환하도록 설정
        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);

                    // 기존 진술이 아닌 새 객체로 저장 요청하는지 확인
                    assertNull(statement.getStatementId());

                    // DB가 새 진술 번호를 생성하는 상황 재현
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
                        OFFICER_ID,
                        typedRequest
                );

        // 저장 메서드에 전달된 진술 정보 검사
        ArgumentCaptor<Statement> captor =
                ArgumentCaptor.forClass(Statement.class);

        verify(statementRepository).save(captor.capture());

        Statement statement = captor.getValue();

        assertEquals(WITNESS_ID, statement.getWitnessId());
        assertEquals(statementType, statement.getStatementType());
        assertEquals(typedRequest.content(), statement.getContent());
        assertNotNull(statement.getCreatedAt());

        // 저장 결과의 응답 DTO 변환 검사
        assertEquals(Integer.valueOf(10), response.statementId());
        assertEquals(statementType, response.statementType());
        assertEquals(typedRequest.content(), response.content());
        assertEquals(statement.getCreatedAt(), response.createdAt());

        // 등록 전에 담당자·수사중 상태 검사를 호출하는지 확인
        verify(investigationAccessService)
                .findInvestigatingCase(CASE_ID, OFFICER_ID);

        // 기존 진술 조회·삭제 없이 새 진술 저장만 호출
        verifyNoMoreInteractions(statementRepository);
    }

    // ==================== 접근 검사 실패 테스트 ====================

    @ParameterizedTest(name = "접근 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404, 409})
    @DisplayName("접근 검사에 실패하면 목격자 조회와 진술 저장을 중단한다")
    void rejectWhenAccessFails(int status) {

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
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        OFFICER_ID,
                        request
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(
                witnessRepository,
                statementRepository
        );
    }

    // ==================== 목격자·진술 유형 검증 테스트 ====================

    @Test
    @DisplayName("목격자가 없으면 404 예외가 발생하고 저장하지 않는다")
    void rejectMissingWitness() {

        prepareAccess();

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        OFFICER_ID,
                        request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("다른 사건의 목격자이면 404 예외가 발생한다")
    void rejectWitnessFromAnotherCase() {

        prepareAccess();

        // 선택한 사건과 목격자의 소속 사건이 다른 상황
        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(
                        savedWitness("SMPA-2026-0406")
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        OFFICER_ID,
                        request
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("INITIAL 유형이면 400 예외가 발생하고 저장하지 않는다")
    void rejectInitialStatement() {

        prepareAccess();

        when(witnessRepository.findById(WITNESS_ID))
                .thenReturn(Optional.of(savedWitness(CASE_ID)));

        StatementCreateRequest initialRequest =
                new StatementCreateRequest(
                        StatementType.INITIAL,
                        "최초 진술 유형으로 추가 등록을 시도합니다."
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statementService.createStatement(
                        CASE_ID,
                        WITNESS_ID,
                        OFFICER_ID,
                        initialRequest
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(statementRepository);
    }

    // ==================== 테스트 준비용 메서드 ====================

    // 공통 접근 검사를 통과한 상황 재현
    private void prepareAccess() {
        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase(CASE_ID));
    }

    // 사건 소속 비교에 사용할 테스트 객체
    // 권한·상태 판단은 가짜 AccessService가 담당
    private CaseFile savedCase(String caseId) {
        return new CaseFile(
                caseId,
                null,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                BASE_TIME,
                "테스트 장소"
        );
    }

    // DB에서 조회한 목격자 역할의 테스트 객체
    private Witness savedWitness(String caseId) {
        return new Witness(
                WITNESS_ID,
                savedCase(caseId),
                "테스트 목격자",
                "010-1234-5678",
                "인근 주민",
                BASE_TIME
        );
    }
}