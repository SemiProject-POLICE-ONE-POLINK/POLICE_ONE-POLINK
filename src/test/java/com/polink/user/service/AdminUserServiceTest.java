package com.polink.user.service;

import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.dto.response.AdminUserListResponse;
import com.polink.user.dto.response.AssignablePoliceListResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaseRepository caseRepository;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                caseRepository
        );
    }

    @Test
    void getAssignablePolice_success() {
        User admin = new User(
                "admin01", "test-hash", "관리자", "02-3150-0100"
        );
        ReflectionTestUtils.setField(admin, "userId", 11);
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);

        User firstOfficer = new User(
                "golombo", "test-hash", "골롬보", "02-3150-0110"
        );
        ReflectionTestUtils.setField(firstOfficer, "userId", 6);
        ReflectionTestUtils.setField(
                firstOfficer,
                "role",
                UserRole.POLICE
        );

        User secondOfficer = new User(
                "shin", "test-hash", "신형선", "02-3150-0111"
        );
        ReflectionTestUtils.setField(secondOfficer, "userId", 7);
        ReflectionTestUtils.setField(
                secondOfficer,
                "role",
                UserRole.POLICE
        );

        when(userRepository.findById(11))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByRoleOrderByNameAsc(UserRole.POLICE))
                .thenReturn(List.of(firstOfficer, secondOfficer));
        when(caseRepository
                .countByAssignedOfficer_UserIdAndStatus(
                        6,
                        CaseStatus.INVESTIGATING
                ))
                .thenReturn(2L);
        when(caseRepository
                .countByAssignedOfficer_UserIdAndStatus(
                        7,
                        CaseStatus.INVESTIGATING
                ))
                .thenReturn(0L);

        AssignablePoliceListResponse response =
                adminUserService.getAssignablePolice(11);

        assertEquals(2, response.officers().size());

        assertEquals(
                6,
                response.officers().get(0).userId()
        );
        assertEquals(
                "골롬보",
                response.officers().get(0).name()
        );
        assertEquals(
                2L,
                response.officers().get(0).investigatingCaseCount()
        );

        assertEquals(
                7,
                response.officers().get(1).userId()
        );
        assertEquals(
                "신형선",
                response.officers().get(1).name()
        );
        assertEquals(
                0L,
                response.officers().get(1).investigatingCaseCount()
        );

        verify(userRepository)
                .findByRoleOrderByNameAsc(UserRole.POLICE);
        verify(caseRepository)
                .countByAssignedOfficer_UserIdAndStatus(
                        6,
                        CaseStatus.INVESTIGATING
                );
        verify(caseRepository)
                .countByAssignedOfficer_UserIdAndStatus(
                        7,
                        CaseStatus.INVESTIGATING
                );
    }

    @Test
    void getAllUsers_success() {
        User citizen = new User(
                "citizen01",
                "citizen-hash",
                "테스트시민",
                "010-1111-1111"
        );
        ReflectionTestUtils.setField(citizen, "userId", 1);

        User police = new User(
                "police01",
                "police-hash",
                "테스트경찰",
                "010-2222-2222"
        );
        ReflectionTestUtils.setField(police, "userId", 7);
        ReflectionTestUtils.setField(
                police,
                "role",
                UserRole.POLICE
        );

        User admin = new User(
                "admin01",
                "admin-hash",
                "테스트관리자",
                "010-3333-3333"
        );
        ReflectionTestUtils.setField(admin, "userId", 11);
        ReflectionTestUtils.setField(
                admin,
                "role",
                UserRole.ADMIN
        );

        when(userRepository.findById(11))
                .thenReturn(Optional.of(admin));
        when(userRepository.findAllByOrderByUserIdAsc())
                .thenReturn(List.of(citizen, police, admin));

        AdminUserListResponse response =
                adminUserService.getAllUsers(11);

        assertEquals(3, response.users().size());

        assertEquals(1, response.users().get(0).userId());
        assertEquals(
                "citizen01",
                response.users().get(0).userName()
        );
        assertEquals(
                "테스트시민",
                response.users().get(0).name()
        );
        assertEquals(
                "010-1111-1111",
                response.users().get(0).phone()
        );
        assertEquals(
                UserRole.CITIZEN,
                response.users().get(0).role()
        );

        assertEquals(7, response.users().get(1).userId());
        assertEquals(
                UserRole.POLICE,
                response.users().get(1).role()
        );

        assertEquals(11, response.users().get(2).userId());
        assertEquals(
                UserRole.ADMIN,
                response.users().get(2).role()
        );

        verify(userRepository)
                .findAllByOrderByUserIdAsc();
        verifyNoInteractions(caseRepository);
    }

    @Test
    void getAllUsers_withoutLogin_throwsUnauthorized() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAllUsers(null)
        );

        assertEquals(401, exception.getStatusCode().value());
        assertEquals(
                "로그인 사용자 정보가 필요합니다.",
                exception.getReason()
        );

        verifyNoInteractions(userRepository, caseRepository);
    }

    @Test
    void getAllUsers_adminNotFound_throwsNotFound() {
        when(userRepository.findById(999))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAllUsers(999)
        );

        assertEquals(404, exception.getStatusCode().value());
        assertEquals(
                "회원을 찾을 수 없습니다.",
                exception.getReason()
        );

        verify(userRepository).findById(999);
        verify(userRepository, never())
                .findAllByOrderByUserIdAsc();
        verifyNoInteractions(caseRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "POLICE"}
    )
    void getAllUsers_nonAdmin_throwsForbidden(
            UserRole role
    ) {
        User nonAdmin = new User(
                "nonAdmin",
                "test-hash",
                "일반 사용자",
                "010-4444-4444"
        );
        ReflectionTestUtils.setField(nonAdmin, "userId", 12);
        ReflectionTestUtils.setField(nonAdmin, "role", role);

        when(userRepository.findById(12))
                .thenReturn(Optional.of(nonAdmin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAllUsers(12)
        );

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(
                "회원 목록 조회는 관리자만 가능합니다.",
                exception.getReason()
        );

        verify(userRepository).findById(12);
        verify(userRepository, never())
                .findAllByOrderByUserIdAsc();
        verifyNoInteractions(caseRepository);
    }

    @Test
    void getAssignablePolice_withoutLogin_throwsUnauthorized() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAssignablePolice(null)
        );

        assertEquals(401, exception.getStatusCode().value());
        assertEquals(
                "로그인 사용자 정보가 필요합니다.",
                exception.getReason()
        );

        verifyNoInteractions(userRepository, caseRepository);
    }

    @Test
    void getAssignablePolice_adminNotFound_throwsNotFound() {
        when(userRepository.findById(999))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAssignablePolice(999)
        );

        assertEquals(404, exception.getStatusCode().value());
        assertEquals(
                "회원을 찾을 수 없습니다.",
                exception.getReason()
        );

        verify(userRepository).findById(999);
        verifyNoInteractions(caseRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = UserRole.class,
            names = {"CITIZEN", "POLICE"}
    )
    void getAssignablePolice_nonAdmin_throwsForbidden(
            UserRole role
    ) {
        User nonAdmin = new User(
                "nonAdmin",
                "test-hash",
                "일반 사용자",
                "010-1234-5678"
        );
        ReflectionTestUtils.setField(nonAdmin, "userId", 12);
        ReflectionTestUtils.setField(nonAdmin, "role", role);

        when(userRepository.findById(12))
                .thenReturn(Optional.of(nonAdmin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminUserService.getAssignablePolice(12)
        );

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(
                "배정 가능한 경찰관 목록 조회는 관리자만 가능합니다.",
                exception.getReason()
        );

        verify(userRepository).findById(12);
        verify(userRepository, never())
                .findByRoleOrderByNameAsc(UserRole.POLICE);
        verifyNoInteractions(caseRepository);
    }
}