package com.polink.user.service;

import com.polink.user.dto.response.MyInfoResponse;
import com.polink.user.entity.User;
import com.polink.user.entity.UserRole;
import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 실제 DB 없이 내 정보 조회 결과와 오류 처리를 검사
@ExtendWith(MockitoExtension.class)
class MyInfoServiceTest {

    private static final Integer USER_ID = 7;

    @Mock
    private UserRepository userRepository;

    private MyInfoService myInfoService;

    @BeforeEach
    void setUp() {
        myInfoService = new MyInfoService(userRepository);
    }

    // ==================== 정상 조회 ====================

    @ParameterizedTest(name = "{0} 회원 본인 정보 조회")
    @EnumSource(UserRole.class)
    @DisplayName("시민·경찰·관리자 모두 본인 정보를 조회할 수 있다")
    void findMyInfoSuccess(UserRole role) {

        // 준비: 조회할 회원과 Repository 반환값 설정
        User user = createUser(role);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        // 실행
        MyInfoResponse response =
                myInfoService.findMyInfo(USER_ID);

        // 확인: 조회한 회원의 정보가 응답에 그대로 반영됨
        assertAll(
                () -> assertEquals(
                        USER_ID, response.userId()
                ),
                () -> assertEquals(
                        user.getUserName(), response.userName()
                ),
                () -> assertEquals(
                        user.getName(), response.name()
                ),
                () -> assertEquals(
                        user.getPhone(), response.phone()
                ),
                () -> assertEquals(
                        role, response.role()
                )
        );

        // 확인: 회원 조회만 호출하고 저장 등 다른 작업은 하지 않음
        verify(userRepository).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    // ==================== 로그인 정보 없음 ====================

    @Test
    @DisplayName("로그인 회원 번호가 없으면 401을 반환하고 DB를 조회하지 않는다")
    void rejectWithoutLogin() {

        // 실행: 로그인 회원 번호 없이 조회
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> myInfoService.findMyInfo(null)
        );

        // 확인: 인증 오류와 사용자 안내 문구
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        assertEquals(
                "로그인 후 이용해주세요.",
                exception.getReason()
        );

        // 확인: 로그인 정보가 없으므로 회원 조회도 하지 않음
        verifyNoInteractions(userRepository);
    }

    // ==================== 회원 없음 ====================

    @Test
    @DisplayName("회원 번호에 해당하는 사용자가 없으면 404를 반환한다")
    void rejectMissingUser() {

        // 준비: 해당 회원 번호로 조회된 사용자가 없는 상황
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        // 실행
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> myInfoService.findMyInfo(USER_ID)
        );

        // 확인: 회원 없음 상태와 사용자 안내 문구
        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "회원 정보를 확인할 수 없습니다. 다시 로그인해주세요.",
                exception.getReason()
        );

        // 확인: 회원 조회만 하고 저장 등 다른 작업은 하지 않음
        verify(userRepository).findById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    // ==================== 테스트용 회원 생성 ====================

    private User createUser(UserRole role) {
        User user = new User(
                "test-user",
                "test-encoded-password",
                "테스트 회원",
                "010-0000-0000"
        );

        // 실제 DB 대신 회원 번호와 역할을 테스트에서 설정
        ReflectionTestUtils.setField(
                user, "userId", USER_ID
        );

        ReflectionTestUtils.setField(
                user, "role", role
        );

        return user;
    }
}