package com.polink.user.service;

import com.polink.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.polink.user.dto.request.SignupRequest;
import com.polink.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import com.polink.user.dto.response.SignupResponse;
import com.polink.user.entity.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void signup_duplicateUserName() {
        // 준비: 요청 아이디와 아이디 중복 조회 결과를 설정한다.
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(
                request, "userName", "signup_test01");

        when(userRepository.existsByUserName("signup_test01"))
                .thenReturn(true);

        // 실행: 가입 요청이 예외로 거절되는지 확인한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.signup(request)
        );

        // 검증: 409 응답이며 회원 저장은 호출되지 않아야 한다.
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("이미 사용 중인 아이디입니다.",
                exception.getReason());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signup_duplicatePhone() {
        // 준비: 아이디는 중복이 없고, 연락처는 이미 등록된 상태다.
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(
                request, "userName", "signup_test02");
        ReflectionTestUtils.setField(
                request, "phone", "010-0000-9001");

        when(userRepository.existsByUserName("signup_test02"))
                .thenReturn(false);

        when(userRepository.existsByPhone("010-0000-9001"))
                .thenReturn(true);

        // 실행: 연락처 중복으로 가입이 거절되어야 한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> userService.signup(request)
        );

        // 검증: 409 응답이며 회원 저장은 호출되지 않아야 한다.
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("이미 등록된 연락처입니다.",
                exception.getReason());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signup_success() {
        // 준비: 가입 요청과 중복이 없는 상태를 설정한다.
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "userName", "newuser");
        ReflectionTestUtils.setField(request, "password", "Test1234!");
        ReflectionTestUtils.setField(request, "name", "테스트회원");
        ReflectionTestUtils.setField(request, "phone", "010-0000-9003");

        when(userRepository.existsByUserName("newuser"))
                .thenReturn(false);
        when(userRepository.existsByPhone("010-0000-9003"))
                .thenReturn(false);

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "userId", 1);
                    return user;
                });

        // 실행
        SignupResponse response = userService.signup(request);

        // 검증: 저장할 회원의 정보와 비밀번호를 확인한다.
        var userCaptor =
                org.mockito.ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("newuser", savedUser.getUserName());
        assertEquals("테스트회원", savedUser.getName());
        assertEquals("010-0000-9003", savedUser.getPhone());
        assertEquals(UserRole.CITIZEN, savedUser.getRole());

        assertNotEquals("Test1234!", savedUser.getPassword());
        assertTrue(passwordEncoder.matches(
                "Test1234!", savedUser.getPassword()));

        assertEquals(Integer.valueOf(1), response.getUserId());
        assertEquals("newuser", response.getUserName());
    }
}