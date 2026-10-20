package com.polink.user.service;

import com.polink.user.dto.request.LoginRequest;
import com.polink.user.dto.request.SignupRequest;
import com.polink.user.dto.response.SignupResponse;
import com.polink.user.entity.User;
import com.polink.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 회원가입: 중복 여부를 확인하고 비밀번호를 BCrypt로 변환한 뒤 회원을 저장
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        if (userRepository.existsByUserName(request.getUserName())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 등록된 연락처입니다.");
        }

        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getUserName(),
                encodedPassword,
                request.getName(),
                request.getPhone()
        );

        User savedUser = userRepository.save(user);

        return new SignupResponse(
                savedUser.getUserId(),
                savedUser.getUserName()
        );
    }

    // 로그인: 아이디로 회원을 찾고 입력 비밀번호가 저장된 해시와 일치하는지 확인
    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        User user = userRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "아이디 또는 비밀번호를 확인해주세요."
                ));

        // 입력 비밀번호와 DB에 저장된 BCrypt 해시를 비교
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "아이디 또는 비밀번호를 확인해주세요."
            );
        }

        return user;
    }
}
