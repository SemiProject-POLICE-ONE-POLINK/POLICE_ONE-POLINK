package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.entity.Statement;
import com.polink.investigation.entity.StatementType;
import com.polink.investigation.repository.StatementRepository;
import com.polink.investigation.repository.WitnessRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 개발 MySQL 대신 별도의 H2 테스트 DB에서 실행
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false",
        "spring.liquibase.enabled=false"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.ANY
)
@Import({
        WitnessService.class,
        InvestigationAccessService.class
})
// 테스트 전체를 자동 롤백하는 대신 Service의 트랜잭션 결과를 확인
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WitnessRollbackIntegrationTest {

    private static final String CASE_ID = "SMPA-2026-0405";

    @Autowired
    private WitnessService witnessService;

    @Autowired
    private WitnessRepository witnessRepository;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Integer officerId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        // 테스트 준비 데이터는 별도 트랜잭션에서 저장·커밋
        transaction.executeWithoutResult(status -> {

            // 이 테스트의 H2 데이터만 초기화
            // 참조하는 테이블부터 순서대로 삭제
            statementRepository.deleteAllInBatch();
            witnessRepository.deleteAllInBatch();
            caseRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            // 테스트용 담당 경찰 생성
            User officer = new User(
                    "rollback-test-police",
                    "test-encoded-password",
                    "롤백 테스트 경찰",
                    "010-0000-0000"
            );

            ReflectionTestUtils.setField(
                    officer,
                    "role",
                    UserRole.POLICE
            );

            User savedOfficer =
                    userRepository.saveAndFlush(officer);

            officerId = savedOfficer.getUserId();

            // 담당 경찰이 배정된 수사중 사건 생성
            CaseFile caseFile = new CaseFile(
                    CASE_ID,
                    savedOfficer,
                    "롤백 테스트 사건",
                    CaseType.THEFT,
                    "목격자와 최초 진술의 저장 결과 확인",
                    LocalDateTime.of(2026, 10, 20, 10, 0),
                    "테스트 장소"
            );

            caseFile.assignOfficer(savedOfficer);

            caseRepository.saveAndFlush(caseFile);
        });
    }

    // ==================== 정상 저장 ====================

    @Test
    @DisplayName("정상 등록하면 목격자와 최초 진술이 모두 DB에 저장된다")
    void saveWitnessAndInitialStatement() {

        // 준비: 정상 목격자 등록 요청
        WitnessCreateRequest request =
                createRequest("흰색 차량을 목격했습니다.");

        // 실행: 실제 Service와 실제 Repository 사용
        WitnessCreateResponse response =
                witnessService.createWitness(
                        CASE_ID,
                        officerId,
                        request
                );

        // 확인: Service 트랜잭션 종료 후 DB를 다시 조회
        assertTrue(
                witnessRepository.existsById(response.witnessId())
        );

        Statement statement = statementRepository
                .findById(response.statementId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        response.witnessId(),
                        statement.getWitnessId()
                ),
                () -> assertEquals(
                        StatementType.INITIAL,
                        statement.getStatementType()
                ),
                () -> assertEquals(
                        request.content(),
                        statement.getContent()
                ),
                () -> assertEquals(
                        1L,
                        witnessRepository.count()
                ),
                () -> assertEquals(
                        1L,
                        statementRepository.count()
                )
        );
    }

    // ==================== 저장 실패·실제 롤백 ====================

    @Test
    @DisplayName("최초 진술 저장 실패 시 새 목격자는 롤백되고 기존 자료는 유지된다")
    void rollbackNewWitnessWhenStatementFails() {

        // 준비 1: 기존에 정상 등록된 목격자와 진술을 남겨둠
        WitnessCreateResponse existing =
                witnessService.createWitness(
                        CASE_ID,
                        officerId,
                        createRequest("기존에 정상 저장된 최초 진술")
                );

        assertEquals(1L, witnessRepository.count());
        assertEquals(1L, statementRepository.count());

        // 준비 2: 진술 내용의 NOT NULL 조건을 위반해 저장 실패 유도
        // Controller의 @Valid를 거치지 않고 Service를 직접 호출
        WitnessCreateRequest invalidRequest = createRequest(null);

        // 실행: 목격자 INSERT 이후 최초 진술 저장 단계에서 실패
        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> witnessService.createWitness(
                        CASE_ID,
                        officerId,
                        invalidRequest
                )
        );

        // 확인: 진술 content 관련 제약 위반으로 실패한 것인지 검사
        String causeMessage =
                exception.getMostSpecificCause().getMessage();

        assertNotNull(causeMessage);
        assertTrue(
                causeMessage.toLowerCase(java.util.Locale.ROOT)
                        .contains("content")
        );

        // 확인: Service 트랜잭션이 끝난 뒤 DB를 새로 조회
        // 두 번째 목격자는 02번으로 저장을 시도했지만 롤백되어 없어야 함
        assertFalse(
                witnessRepository.existsById("W-2026-0405-02")
        );

        // 실패 전부터 있던 목격자와 진술은 유지되어야 함
        assertTrue(
                witnessRepository.existsById(existing.witnessId())
        );

        assertTrue(
                statementRepository.existsById(existing.statementId())
        );

        // 실패한 요청으로 목격자나 진술이 추가되지 않았는지 확인
        assertEquals(1L, witnessRepository.count());
        assertEquals(1L, statementRepository.count());

        List<Statement> statements = statementRepository
                .findByWitnessIdInOrderByCreatedAtAscStatementIdAsc(
                        List.of(existing.witnessId())
                );

        assertEquals(1, statements.size());

        assertEquals(
                "기존에 정상 저장된 최초 진술",
                statements.get(0).getContent()
        );
    }

    // ==================== 테스트 요청 생성 ====================

    private WitnessCreateRequest createRequest(String content) {
        return new WitnessCreateRequest(
                "테스트 목격자",
                "010-0000-0000",
                "현장 주변 상인",
                content
        );
    }
}