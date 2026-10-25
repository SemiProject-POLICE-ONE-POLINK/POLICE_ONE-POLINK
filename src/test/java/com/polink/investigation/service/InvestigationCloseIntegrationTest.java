package com.polink.investigation.service;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseCloseRepository;
import com.polink.casefile.repository.CaseRepository;
import com.polink.casefile.service.CaseNumberGenerator;
import com.polink.casefile.service.CaseService;
import com.polink.investigation.dto.request.InvestigationCloseRequest;
import com.polink.investigation.dto.response.InvestigationCloseResponse;
import com.polink.investigation.entity.InvestigationLog;
import com.polink.investigation.repository.InvestigationLogRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

// 개발 DB 대신 별도의 테스트 DB에서 실제 저장·롤백 확인
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
        InvestigationCloseService.class,
        InvestigationAccessService.class,
        CaseService.class
})
// 테스트 자체의 트랜잭션 대신 각 Service의 실제 트랜잭션 사용
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvestigationCloseIntegrationTest {

    private static final String CASE_ID = "SMPA-2026-0405";

    @Autowired
    private InvestigationCloseService investigationCloseService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CaseCloseRepository caseCloseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 평소에는 실제 Repository로 동작
    // 롤백 테스트에서만 저장 실패를 발생시킴
    @MockitoSpyBean
    private InvestigationLogRepository investigationLogRepository;

    // CaseService 생성에 필요하지만 이 테스트에서는 사건을 생성하지 않음
    @MockitoBean
    private CaseNumberGenerator caseNumberGenerator;

    private Integer officerId;
    private Integer otherOfficerId;
    private Integer adminId;
    private Integer citizenId;

    private final InvestigationCloseRequest request =
            new InvestigationCloseRequest("조사 및 증거 확인 완료");

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        // 준비 데이터는 별도 트랜잭션으로 저장
        transaction.executeWithoutResult(status -> {
            // 이 테스트 DB에 저장된 데이터만 정리
            caseCloseRepository.deleteAllInBatch();
            investigationLogRepository.deleteAllInBatch();
            caseRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            User officer = saveUser("close-police", UserRole.POLICE);
            User otherOfficer = saveUser("close-other", UserRole.POLICE);
            User admin = saveUser("close-admin", UserRole.ADMIN);
            User citizen = saveUser("close-citizen", UserRole.CITIZEN);

            officerId = officer.getUserId();
            otherOfficerId = otherOfficer.getUserId();
            adminId = admin.getUserId();
            citizenId = citizen.getUserId();

            CaseFile caseFile = new CaseFile(
                    CASE_ID,
                    citizen,
                    "수사 종결 테스트 사건",
                    CaseType.THEFT,
                    "수사 종결과 관리자 최종 종결 연결 확인",
                    LocalDateTime.of(2026, 10, 25, 10, 0),
                    "테스트 장소"
            );

            // 담당 경찰 배정 시 수사중 상태로 변경
            caseFile.assignOfficer(officer);
            caseRepository.saveAndFlush(caseFile);
        });
    }

    @Test
    @DisplayName("담당 경찰이 수사를 종결하면 종결 대기 상태와 기록이 저장된다")
    void closeInvestigationChangesDatabaseStatus() {
        InvestigationCloseResponse response =
                investigationCloseService.closeInvestigation(
                        CASE_ID,
                        officerId,
                        request
                );

        // Service 트랜잭션 종료 후 DB를 다시 조회
        assertCaseStatus(CaseStatus.INVESTIGATION_DONE);

        assertEquals("INVESTIGATION_DONE", response.status());
        assertEquals(officerId, response.officerId());
        assertEquals(1L, investigationLogRepository.count());

        InvestigationLog savedLog = investigationLogRepository
                .findById(response.logId())
                .orElseThrow();

        assertEquals(
                "수사 종결: " + request.closeReason(),
                savedLog.getContent()
        );
        assertNotNull(savedLog.getInvestigatedAt());

        // 경찰의 수사 종결만으로 최종 사건 종결 기록은 생성되지 않음
        assertFalse(caseCloseRepository.existsById(CASE_ID));
    }

    @Test
    @DisplayName("로그인 사용자 번호가 없으면 수사 종결을 거부한다")
    void rejectWithoutLogin() {
        assertCloseRejected(401, null);
        assertCaseStatus(CaseStatus.INVESTIGATING);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "CITIZEN"})
    @DisplayName("관리자와 시민은 경찰의 수사 종결 기능을 사용할 수 없다")
    void rejectNonPolice(UserRole role) {
        Integer userId = role == UserRole.ADMIN ? adminId : citizenId;

        assertCloseRejected(403, userId);
        assertCaseStatus(CaseStatus.INVESTIGATING);
    }

    @Test
    @DisplayName("다른 경찰은 담당 사건의 수사를 종결할 수 없다")
    void rejectOtherOfficer() {
        assertCloseRejected(403, otherOfficerId);
        assertCaseStatus(CaseStatus.INVESTIGATING);
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {
                    "WAITING",
                    "INVESTIGATION_DONE",
                    "CLOSED",
                    "CANCELED"
            }
    )
    @DisplayName("수사중이 아닌 사건의 수사 종결을 거부한다")
    void rejectNonInvestigatingStatus(CaseStatus caseStatus) {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            CaseFile caseFile = caseRepository.findById(CASE_ID)
                    .orElseThrow();

            // 각 상태에서의 거부 여부를 확인하기 위한 테스트 데이터 설정
            ReflectionTestUtils.setField(caseFile, "status", caseStatus);
        });

        assertCloseRejected(409, officerId);
        assertCaseStatus(caseStatus);
    }

    @Test
    @DisplayName("이미 종결 대기인 사건에 다시 종결 요청하면 기록이 추가되지 않는다")
    void rejectRepeatedClose() {
        investigationCloseService.closeInvestigation(
                CASE_ID,
                officerId,
                request
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationCloseService.closeInvestigation(
                        CASE_ID,
                        officerId,
                        request
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        assertCaseStatus(CaseStatus.INVESTIGATION_DONE);
        assertEquals(1L, investigationLogRepository.count());
    }

    @Test
    @DisplayName("종결 기록 저장에 실패하면 사건 상태 변경도 롤백된다")
    void rollbackStatusWhenLogSaveFails() {
        // 저장 오류는 모의로 발생시키고 트랜잭션 롤백은 실제 DB로 확인
        doThrow(new DataIntegrityViolationException("테스트용 저장 실패"))
                .when(investigationLogRepository)
                .save(any(InvestigationLog.class));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> investigationCloseService.closeInvestigation(
                        CASE_ID,
                        officerId,
                        request
                )
        );

        assertCaseStatus(CaseStatus.INVESTIGATING);
        assertEquals(0L, investigationLogRepository.count());
        assertFalse(caseCloseRepository.existsById(CASE_ID));
    }

    @Test
    @DisplayName("관리자는 수사중인 사건을 바로 최종 종결할 수 없다")
    void rejectAdminCloseBeforeInvestigationDone() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> caseService.closeCase(
                        adminId,
                        CASE_ID,
                        caseCloseRequest()
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        assertCaseStatus(CaseStatus.INVESTIGATING);
        assertFalse(caseCloseRepository.existsById(CASE_ID));
    }

    @Test
    @DisplayName("담당 경찰의 수사 종결 후 관리자가 사건을 최종 종결한다")
    void closeCaseAfterInvestigationDone() {
        investigationCloseService.closeInvestigation(
                CASE_ID,
                officerId,
                request
        );

        assertCaseStatus(CaseStatus.INVESTIGATION_DONE);

        caseService.closeCase(
                adminId,
                CASE_ID,
                caseCloseRequest()
        );

        assertCaseStatus(CaseStatus.CLOSED);
        assertTrue(caseCloseRepository.existsById(CASE_ID));
        assertEquals(1L, investigationLogRepository.count());
    }

    private User saveUser(String username, UserRole role) {
        User user = new User(
                username,
                "test-encoded-password",
                "테스트 회원",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(user, "role", role);

        return userRepository.saveAndFlush(user);
    }

    private CaseCloseRequest caseCloseRequest() {
        CaseCloseRequest request = new CaseCloseRequest();

        ReflectionTestUtils.setField(
                request,
                "closeReason",
                "담당 경찰의 수사 종결 내용 확인 완료"
        );

        return request;
    }

    private void assertCaseStatus(CaseStatus expected) {
        CaseFile saved = caseRepository.findById(CASE_ID)
                .orElseThrow();

        assertEquals(expected, saved.getStatus());
    }

    private void assertCloseRejected(int expectedStatus, Integer userId) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationCloseService.closeInvestigation(
                        CASE_ID,
                        userId,
                        request
                )
        );

        assertEquals(
                expectedStatus,
                exception.getStatusCode().value()
        );
        assertEquals(0L, investigationLogRepository.count());
        assertFalse(caseCloseRepository.existsById(CASE_ID));
    }
}