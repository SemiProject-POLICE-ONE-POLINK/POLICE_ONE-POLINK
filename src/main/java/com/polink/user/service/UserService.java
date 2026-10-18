package com.polink.user.service;

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
}
