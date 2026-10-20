package com.polink.investigation.service;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 DB 없이 공통 접근 검사 규칙 확인
@ExtendWith(MockitoExtension.class)
class InvestigationAccessServiceTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final Integer OFFICER_ID = 7;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InvestigationAccessService investigationAccessService;

    // ==================== 정상 접근 테스트 ====================

    @ParameterizedTest(name = "{0} 사건 조회 허용")
    @EnumSource(CaseStatus.class)
    @DisplayName("담당 경찰관은 사건 상태와 관계없이 조회할 수 있다")
    void findAssignedCaseRegardlessOfStatus(CaseStatus status) {

        User officer = savedUser(OFFICER_ID, UserRole.POLICE);
        CaseFile caseFile = savedCase(officer);

        // 대기중·수사중·종결·취소 상태를 각각 재현
        ReflectionTestUtils.setField(caseFile, "status", status);

        prepareAccess(officer, caseFile);

        CaseFile result =
                investigationAccessService.findAssignedCase(
                        CASE_ID,
                        OFFICER_ID
                );

        // 같은 사건 정보를 반환하는지 확인
        assertSame(caseFile, result);
    }

    @Test
    @DisplayName("담당 경찰관이고 사건이 수사중이면 등록용 검사를 통과한다")
    void findInvestigatingCase() {

        User officer = savedUser(OFFICER_ID, UserRole.POLICE);
        CaseFile caseFile = savedCase(officer);

        prepareAccess(officer, caseFile);

        CaseFile result =
                investigationAccessService.findInvestigatingCase(
                        CASE_ID,
                        OFFICER_ID
                );

        assertSame(caseFile, result);
        assertEquals(CaseStatus.INVESTIGATING, result.getStatus());
    }

    // ==================== 로그인·권한 검사 테스트 ====================

    @Test
    @DisplayName("로그인 정보가 없으면 조회·등록 모두 401로 거부한다")
    void rejectMissingLogin() {

        assertAccessRejected(HttpStatus.UNAUTHORIZED, null);

        // 로그인 정보가 없으면 DB 조회도 하지 않음
        verifyNoInteractions(
                userRepository,
                caseRepository
        );
    }

    @Test
    @DisplayName("로그인 회원이 없으면 조회·등록 모두 401로 거부한다")
    void rejectMissingUser() {

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.empty());

        assertAccessRejected(HttpStatus.UNAUTHORIZED, OFFICER_ID);

        verifyNoInteractions(caseRepository);
    }

    @ParameterizedTest(name = "{0} 역할 접근 거부")
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "ADMIN"}
    )
    @DisplayName("경찰관이 아니면 조회·등록 모두 403으로 거부한다")
    void rejectNonPolice(UserRole role) {

        User user = savedUser(OFFICER_ID, role);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(user));

        assertAccessRejected(HttpStatus.FORBIDDEN, OFFICER_ID);

        // 역할 검사에서 실패했으므로 사건 조회 생략
        verifyNoInteractions(caseRepository);
    }

    @Test
    @DisplayName("사건이 없으면 조회·등록 모두 404로 거부한다")
    void rejectMissingCase() {

        User officer = savedUser(OFFICER_ID, UserRole.POLICE);

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.empty());

        assertAccessRejected(HttpStatus.NOT_FOUND, OFFICER_ID);
    }

    @ParameterizedTest(name = "사건 담당자 ID: {0}")
    @NullSource
    @ValueSource(ints = {8})
    @DisplayName("담당자가 없거나 다른 경찰관이면 조회·등록 모두 403으로 거부한다")
    void rejectUnassignedOrOtherOfficer(Integer assignedOfficerId) {

        User loggedInOfficer =
                savedUser(OFFICER_ID, UserRole.POLICE);

        User assignedOfficer = assignedOfficerId == null
                ? null
                : savedUser(assignedOfficerId, UserRole.POLICE);

        CaseFile caseFile = savedCase(assignedOfficer);

        prepareAccess(loggedInOfficer, caseFile);

        assertAccessRejected(HttpStatus.FORBIDDEN, OFFICER_ID);
    }

    // ==================== 사건 상태 검사 테스트 ====================

    @ParameterizedTest(name = "{0} 사건 등록 거부")
    @EnumSource(
            value = CaseStatus.class,
            names = {"WAITING", "CLOSED", "CANCELED"}
    )
    @DisplayName("담당 경찰관이어도 수사중이 아니면 등록용 검사는 409로 거부한다")
    void rejectNonInvestigatingCase(CaseStatus status) {

        User officer = savedUser(OFFICER_ID, UserRole.POLICE);
        CaseFile caseFile = savedCase(officer);

        ReflectionTestUtils.setField(caseFile, "status", status);

        prepareAccess(officer, caseFile);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> investigationAccessService.findInvestigatingCase(
                        CASE_ID,
                        OFFICER_ID
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
    }

    // ==================== 테스트 준비·확인용 메서드 ====================

    // 로그인 회원과 사건 조회 결과 설정
    private void prepareAccess(User user, CaseFile caseFile) {

        when(userRepository.findById(OFFICER_ID))
                .thenReturn(Optional.of(user));

        when(caseRepository.findById(CASE_ID))
                .thenReturn(Optional.of(caseFile));
    }

    // 조회용·등록용 검사 모두 같은 접근 거부 결과인지 확인
    private void assertAccessRejected(
            HttpStatus expectedStatus,
            Integer loggedInUserId
    ) {

        ResponseStatusException readException = assertThrows(
                ResponseStatusException.class,
                () -> investigationAccessService.findAssignedCase(
                        CASE_ID,
                        loggedInUserId
                )
        );

        ResponseStatusException writeException = assertThrows(
                ResponseStatusException.class,
                () -> investigationAccessService.findInvestigatingCase(
                        CASE_ID,
                        loggedInUserId
                )
        );

        assertEquals(expectedStatus, readException.getStatusCode());
        assertEquals(expectedStatus, writeException.getStatusCode());
    }

    // 실제 DB 없이 회원 번호·역할을 가진 객체 생성
    private User savedUser(Integer userId, UserRole role) {

        User user = new User(
                "test-user-" + userId,
                "test-password-hash",
                "테스트 회원",
                "010-0000-0000"
        );

        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "role", role);

        return user;
    }

    // 담당자가 있으면 배정하고 수사중 상태로 설정
    private CaseFile savedCase(User assignedOfficer) {

        CaseFile caseFile = new CaseFile(
                CASE_ID,
                assignedOfficer,
                "테스트 사건",
                CaseType.THEFT,
                "테스트 사건 설명",
                LocalDateTime.of(2026, 10, 17, 9, 0),
                "테스트 장소"
        );

        if (assignedOfficer != null) {
            caseFile.assignOfficer(assignedOfficer);
        }

        return caseFile;
    }
}