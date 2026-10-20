package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WitnessServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";

    @Mock
    private WitnessRepository witnessRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private CaseRepository caseRepository;

    @InjectMocks
    private WitnessService witnessService;

    private final WitnessCreateRequest request = new WitnessCreateRequest(
            "테스트 목격자",
            "010-1234-5678",
            "인근 상점 직원",
            "오후 9시경 현장 앞을 지나가는 사람을 봤습니다."
    );

    @Test
    @DisplayName("첫 목격자는 01번으로 등록되고 최초 진술이 연결된다")
    void createFirstWitness() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());
        prepareSuccessfulSave();

        WitnessCreateResponse response =
                witnessService.createWitness(CASE_ID, request);

        ArgumentCaptor<Witness> witnessCaptor =
                ArgumentCaptor.forClass(Witness.class);
        ArgumentCaptor<Statement> statementCaptor =
                ArgumentCaptor.forClass(Statement.class);

        verify(witnessRepository).saveAndFlush(witnessCaptor.capture());
        verify(statementRepository).save(statementCaptor.capture());

        Witness witness = witnessCaptor.getValue();
        Statement statement = statementCaptor.getValue();

        assertEquals("W-2026-0405-01", witness.getWitnessId());
        assertEquals(CASE_ID, witness.getCaseFile().getCaseId());
        assertEquals(request.name(), witness.getName());
        assertEquals(request.phone(), witness.getPhone());
        assertEquals(request.relationship(), witness.getRelationship());
        assertNotNull(witness.getCreatedAt());

        assertEquals(witness.getWitnessId(), statement.getWitnessId());
        assertEquals(request.content(), statement.getContent());
        assertEquals(StatementType.INITIAL, statement.getStatementType());
        assertEquals(witness.getCreatedAt(), statement.getCreatedAt());

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

    @Test
    @DisplayName("조회 순서와 관계없이 최대 순번 다음 번호로 등록된다")
    void createNextWitness() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(
                        savedWitness("W-2026-0405-03"),
                        savedWitness("W-2026-0405-01")
                ));
        prepareSuccessfulSave();

        WitnessCreateResponse response =
                witnessService.createWitness(CASE_ID, request);

        assertEquals("W-2026-0405-04", response.witnessId());
    }

    @Test
    @DisplayName("사건이 없으면 404 예외가 발생하고 저장하지 않는다")
    void rejectMissingCase() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> witnessService.createWitness(CASE_ID, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(witnessRepository, statementRepository);
    }

    @Test
    @DisplayName("최대 순번이 99이면 목격자와 진술을 저장하지 않는다")
    void rejectSequenceOverflow() {
        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(savedCase()));
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(savedWitness("W-2026-0405-99")));

        assertThrows(
                IllegalStateException.class,
                () -> witnessService.createWitness(CASE_ID, request)
        );

        verify(witnessRepository, never())
                .saveAndFlush(any(Witness.class));
        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("목격자를 등록순으로 반환하고 각자의 진술을 연결한다")
    void findWitnessListWithStatements() {
        LocalDateTime firstTime =
                LocalDateTime.of(2026, 10, 17, 9, 0);

        Witness earlierWitness = new Witness(
                "W-2026-0405-02",
                savedCase(),
                "먼저 등록한 목격자",
                "010-2222-3333",
                "인근 주민",
                firstTime
        );
        Witness laterWitness =
                savedWitness("W-2026-0405-01");

        Statement firstStatement = statementForList(
                1,
                earlierWitness.getWitnessId(),
                "현장 앞에서 사람을 봤습니다.",
                StatementType.INITIAL,
                firstTime
        );
        Statement otherStatement = statementForList(
                2,
                laterWitness.getWitnessId(),
                "차량 소리를 들었습니다.",
                StatementType.INITIAL,
                firstTime.plusHours(1)
        );
        Statement additionalStatement = statementForList(
                3,
                earlierWitness.getWitnessId(),
                "검은색 옷을 입고 있었습니다.",
                StatementType.ADDITIONAL,
                firstTime.plusHours(2)
        );

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(laterWitness, earlierWitness));

        List<String> witnessIds = List.of(
                earlierWitness.getWitnessId(),
                laterWitness.getWitnessId()
        );

        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(witnessIds))
                .thenReturn(List.of(
                        firstStatement,
                        otherStatement,
                        additionalStatement
                ));

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(CASE_ID);

        assertEquals(2, responses.size());

        WitnessListResponse firstResponse = responses.get(0);
        WitnessListResponse secondResponse = responses.get(1);

        assertEquals(earlierWitness.getWitnessId(), firstResponse.witnessId());
        assertEquals(laterWitness.getWitnessId(), secondResponse.witnessId());
        assertEquals(earlierWitness.getName(), firstResponse.name());
        assertEquals(earlierWitness.getPhone(), firstResponse.phone());
        assertEquals(
                earlierWitness.getRelationship(),
                firstResponse.relationship()
        );

        assertEquals(2, firstResponse.statements().size());
        assertEquals(
                Integer.valueOf(1),
                firstResponse.statements().get(0).statementId()
        );
        assertEquals(
                "현장 앞에서 사람을 봤습니다.",
                firstResponse.statements().get(0).content()
        );
        assertEquals(
                StatementType.INITIAL,
                firstResponse.statements().get(0).statementType()
        );
        assertEquals(
                firstTime,
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

        assertEquals(1, secondResponse.statements().size());
        assertEquals(
                Integer.valueOf(2),
                secondResponse.statements().get(0).statementId()
        );

        verify(statementRepository)
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(witnessIds);
        verify(witnessRepository, never())
                .saveAndFlush(any(Witness.class));
        verify(statementRepository, never())
                .save(any(Statement.class));
    }

    @Test
    @DisplayName("목격자가 없으면 빈 목록을 반환하고 진술은 조회하지 않는다")
    void findEmptyWitnessList() {
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of());

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(CASE_ID);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verifyNoInteractions(statementRepository);
    }

    @Test
    @DisplayName("목격자에게 연결된 진술이 없으면 빈 진술 목록을 반환한다")
    void findWitnessWithoutStatements() {
        Witness witness = savedWitness("W-2026-0405-01");

        when(caseRepository.existsById(CASE_ID))
                .thenReturn(true);
        when(witnessRepository.findByCaseFile_CaseId(CASE_ID))
                .thenReturn(List.of(witness));
        when(statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(witness.getWitnessId())
                ))
                .thenReturn(List.of());

        List<WitnessListResponse> responses =
                witnessService.findWitnessList(CASE_ID);

        assertEquals(1, responses.size());
        assertEquals(witness.getWitnessId(), responses.get(0).witnessId());
        assertNotNull(responses.get(0).statements());
        assertTrue(responses.get(0).statements().isEmpty());
    }

    @Test
    @DisplayName("사건이 없으면 404 예외가 발생하고 목록을 조회하지 않는다")
    void rejectWitnessListForMissingCase() {
        when(caseRepository.existsById(CASE_ID))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> witnessService.findWitnessList(CASE_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(witnessRepository, statementRepository);
    }

    private void prepareSuccessfulSave() {
        when(witnessRepository.saveAndFlush(any(Witness.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(invocation -> {
                    Statement statement = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            statement,
                            "statementId",
                            1
                    );
                    return statement;
                });
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

    private Witness savedWitness(String witnessId) {
        return new Witness(
                witnessId,
                savedCase(),
                "기존 목격자",
                "010-1111-2222",
                "인근 주민",
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
    }

    private Statement statementForList(
            Integer statementId,
            String witnessId,
            String content,
            StatementType statementType,
            LocalDateTime createdAt
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