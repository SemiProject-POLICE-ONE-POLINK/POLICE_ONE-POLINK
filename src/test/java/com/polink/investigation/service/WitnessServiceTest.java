package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.dto.response.WitnessListResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.entity.Witness;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
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

// 실제 DB 없이 목격자·최초 진술 등록 및 목록 조회 로직 검사
@ExtendWith(MockitoExtension.class)
class WitnessServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 10, 17, 10, 0);

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private StatementRepository statementRepository;

    // 실제 권한 판단 대신 검사 통과·실패 결과를 설정
    @Mock
    private InvestigationAccessService investigationAccessService;

    @InjectMocks
    private WitnessService witnessService;

    private final WitnessCreateRequest request =
            new WitnessCreateRequest(
                    "테스트 목격자",
                    "010-1234-5678",
                    "인근 상점 직원",
                    "오후 9시경 현장 앞을 지나가는 사람을 봤습니다."
            );

    // ==================== 목격자·최초 진술 등록 테스트 ====================

    @Test
    @DisplayName("첫 목격자를 01번으로 등록하고 최초 진술을 연결한다")
    void createFirstWitness() {
        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        prepareSuccessfulSave();

        WitnessCreateResponse response =
                witnessService.createWitness(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        ArgumentCaptor<Witness> witnessCaptor =
                ArgumentCaptor.forClass(Witness.class);

        ArgumentCaptor<Statement> statementCaptor =
                ArgumentCaptor.forClass(Statement.class);

        // 목격자를 먼저 저장한 뒤 최초 진술을 저장하는지 확인
        InOrder order = inOrder(
                witnessRepository,
                statementRepository
        );

        order.verify(witnessRepository)
                .saveAndFlush(witnessCaptor.capture());

        order.verify(statementRepository)
                .save(statementCaptor.capture());

        Witness witness = witnessCaptor.getValue();
        Statement statement = statementCaptor.getValue();

        // 목격자 번호와 입력 정보 확인
        assertEquals("W-2026-0405-01", witness.getWitnessId());
        assertEquals(CASE_ID, witness.getCaseFile().getCaseId());
        assertEquals(request.name(), witness.getName());
        assertEquals(request.phone(), witness.getPhone());
        assertEquals(request.relationship(), witness.getRelationship());
        assertNotNull(witness.getCreatedAt());

        // 같은 목격자에게 최초 진술이 연결됐는지 확인
        assertEquals(witness.getWitnessId(), statement.getWitnessId());
        assertEquals(request.content(), statement.getContent());
        assertEquals(StatementType.INITIAL, statement.getStatementType());
        assertEquals(witness.getCreatedAt(), statement.getCreatedAt());

        // 저장 결과의 응답 DTO 변환 확인
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

        // 등록에는 담당 경찰·수사중 상태 검사 사용
        verify(investigationAccessService)
                .findInvestigatingCase(CASE_ID, OFFICER_ID);

        verify(investigationAccessService, never())
                .findReadableCase(anyString(), anyInt());
    }

    @Test
    @DisplayName("조회 순서와 관계없이 최대 순번 다음 번호로 등록한다")
    void createNextWitness() {
        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedWitness("W-2026-0405-03", BASE_TIME),
                        savedWitness("W-2026-0405-01", BASE_TIME)
                ));

        prepareSuccessfulSave();

        WitnessCreateResponse response =
                witnessService.createWitness(
                        CASE_ID,
                        OFFICER_ID,
                        request
                );

        assertEquals("W-2026-0405-04", response.witnessId());
    }

    @ParameterizedTest(name = "등록 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404, 409})
    @DisplayName("접근 검사에 실패하면 목격자·진술을 조회하거나 저장하지 않는다")
    void rejectRegistrationWhenAccessFails(int status) {
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
                () -> witnessService.createWitness(
                        CASE_ID,
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

    @Test
    @DisplayName("최대 순번이 99이면 목격자와 진술을 저장하지 않는다")
    void rejectSequenceOverflow() {
        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedWitness("W-2026-0405-99", BASE_TIME)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> witnessService.createWitness(
                        CASE_ID,
                        OFFICER_ID,
                        request
                )
        );

        verify(witnessRepository, never())
                .saveAndFlush(any(Witness.class));

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("최초 진술 저장에 실패하면 예외를 그대로 전달한다")
    void propagateStatementSaveFailure() {
        when(investigationAccessService
                .findInvestigatingCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        when(witnessRepository.saveAndFlush(any(Witness.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException saveException =
                new IllegalStateException("테스트용 진술 저장 실패");

        when(statementRepository.save(any(Statement.class)))
                .thenThrow(saveException);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> witnessService.createWitness(
                        CASE_ID,
                        OFFICER_ID,
                        request
                )
        );

        // 실제 DB 롤백 여부는 별도 연동 테스트에서 확인
        assertSame(saveException, exception);

        verify(witnessRepository).saveAndFlush(any(Witness.class));
        verify(statementRepository).save(any(Statement.class));
    }

    // ==================== 목격자·진술 목록 조회 테스트 ====================

    @Test
    @DisplayName("조회 권한 확인 후 목격자를 등록순으로 정렬하고 진술을 연결한다")
    void findWitnessListWithStatements() {
        // 번호는 02지만 등록 일시가 더 빠른 목격자
        Witness earlierWitness = savedWitness(
                "W-2026-0405-02",
                BASE_TIME.minusHours(1)
        );

        Witness laterWitness = savedWitness(
                "W-2026-0405-01",
                BASE_TIME
        );

        Statement firstStatement = savedStatement(
                1,
                earlierWitness.getWitnessId(),
                StatementType.INITIAL,
                BASE_TIME.minusHours(1),
                "현장 앞에서 사람을 봤습니다."
        );

        Statement otherStatement = savedStatement(
                2,
                laterWitness.getWitnessId(),
                StatementType.INITIAL,
                BASE_TIME,
                "차량 소리를 들었습니다."
        );

        Statement additionalStatement = savedStatement(
                3,
                earlierWitness.getWitnessId(),
                StatementType.ADDITIONAL,
                BASE_TIME.plusHours(1),
                "검은색 옷을 입고 있었습니다."
        );

        // 관리자 또는 담당 경찰의 조회 검사를 통과한 상황
        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(laterWitness, earlierWitness));

        List<String> witnessIds = List.of(
                earlierWitness.getWitnessId(),
                laterWitness.getWitnessId()
        );

        // 서로 다른 목격자의 진술이 시간순으로 섞여 반환된 상황
        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(witnessIds))
                .thenReturn(List.of(
                        firstStatement,
                        otherStatement,
                        additionalStatement
                ));

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(
                        CASE_ID,
                        OFFICER_ID
                );

        assertEquals(2, responses.size());

        WitnessListResponse firstResponse = responses.get(0);
        WitnessListResponse secondResponse = responses.get(1);

        // 목격자 번호보다 등록 일시를 우선해 정렬
        assertEquals(earlierWitness.getWitnessId(), firstResponse.witnessId());
        assertEquals(laterWitness.getWitnessId(), secondResponse.witnessId());

        // 목격자 기본 정보 변환 확인
        assertEquals(earlierWitness.getName(), firstResponse.name());
        assertEquals(earlierWitness.getPhone(), firstResponse.phone());
        assertEquals(
                earlierWitness.getRelationship(),
                firstResponse.relationship()
        );

        // 첫 목격자에게 최초·추가 진술만 연결됐는지 확인
        assertEquals(2, firstResponse.statements().size());
        assertEquals(
                Integer.valueOf(1),
                firstResponse.statements().get(0).statementId()
        );
        assertEquals(
                StatementType.INITIAL,
                firstResponse.statements().get(0).statementType()
        );
        assertEquals(
                "현장 앞에서 사람을 봤습니다.",
                firstResponse.statements().get(0).content()
        );
        assertEquals(
                firstStatement.getCreatedAt(),
                firstResponse.statements().get(0).createdAt()
        );
        assertEquals(
                Integer.valueOf(3),
                firstResponse.statements().get(1).statementId()
        );
        assertEquals(
                StatementType.ADDITIONAL,
                firstResponse.statements().get(1).statementType()
        );

        // 다른 목격자의 진술이 섞이지 않았는지 확인
        assertEquals(1, secondResponse.statements().size());
        assertEquals(
                Integer.valueOf(2),
                secondResponse.statements().get(0).statementId()
        );

        // 조회에는 관리자·담당 경찰용 접근 검사 사용
        verify(investigationAccessService)
                .findReadableCase(CASE_ID, OFFICER_ID);

        verify(investigationAccessService, never())
                .findAssignedCase(anyString(), anyInt());

        verify(investigationAccessService, never())
                .findInvestigatingCase(anyString(), anyInt());

        verify(statementRepository)
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(witnessIds);

        verify(witnessRepository, never())
                .saveAndFlush(any(Witness.class));

        verify(statementRepository, never())
                .save(any(Statement.class));
    }

    @Test
    @DisplayName("목격자가 없으면 빈 목록을 반환하고 진술 조회를 생략한다")
    void findEmptyWitnessList() {
        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(
                        CASE_ID,
                        OFFICER_ID
                );

        assertNotNull(responses);
        assertTrue(responses.isEmpty());

        verify(investigationAccessService)
                .findReadableCase(CASE_ID, OFFICER_ID);

        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("연결된 진술이 없으면 목격자 정보와 빈 진술 목록을 반환한다")
    void findWitnessWithoutStatements() {
        Witness witness =
                savedWitness("W-2026-0405-01", BASE_TIME);

        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenReturn(savedCase());

        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(witness));

        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(witness.getWitnessId())
                ))
                .thenReturn(List.of());

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(
                        CASE_ID,
                        OFFICER_ID
                );

        assertEquals(1, responses.size());
        assertEquals(witness.getWitnessId(), responses.get(0).witnessId());
        assertNotNull(responses.get(0).statements());
        assertTrue(responses.get(0).statements().isEmpty());
    }

    @ParameterizedTest(name = "조회 검사 실패 상태: {0}")
    @ValueSource(ints = {401, 403, 404})
    @DisplayName("조회 권한 검사에 실패하면 목격자·진술을 조회하지 않는다")
    void rejectListWhenAccessFails(int status) {
        ResponseStatusException accessException =
                new ResponseStatusException(
                        HttpStatus.valueOf(status),
                        "테스트용 접근 거부"
                );

        when(investigationAccessService
                .findReadableCase(CASE_ID, OFFICER_ID))
                .thenThrow(accessException);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> witnessService.findWitnessList(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertSame(accessException, exception);
        assertEquals(status, exception.getStatusCode().value());

        verifyNoInteractions(
                witnessRepository,
                statementRepository
        );
    }

    // ==================== 테스트 준비용 메서드 ====================

    // 실제 DB 대신 저장 요청으로 전달된 객체를 반환
    private void prepareSuccessfulSave() {
        when(witnessRepository.saveAndFlush(any(Witness.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);

                    // DB가 진술 번호를 생성하는 상황 재현
                    ReflectionTestUtils.setField(
                            statement,
                            "statementId",
                            1
                    );

                    return statement;
                });
    }

    // 응답 변환용 사건 객체
    private CaseFile savedCase() {
        return new CaseFile(
                CASE_ID,
                null,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                BASE_TIME,
                "테스트 장소"
        );
    }

    // 이미 등록된 목격자 역할의 테스트 객체
    private Witness savedWitness(
            String witnessId,
            LocalDateTime createdAt
    ) {
        return new Witness(
                witnessId,
                savedCase(),
                "기존 목격자",
                "010-1111-2222",
                "인근 주민",
                createdAt
        );
    }

    // 조회 결과로 사용할 진술 객체
    private Statement savedStatement(
            Integer statementId,
            String witnessId,
            StatementType statementType,
            LocalDateTime createdAt,
            String content
    ) {
        Statement statement = new Statement(
                witnessId,
                content,
                statementType,
                createdAt
        );

        ReflectionTestUtils.setField(
                statement,
                "statementId",
                statementId
        );

        return statement;
    }
}