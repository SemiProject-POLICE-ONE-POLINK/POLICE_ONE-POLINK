package com.polink.user.controller;

import com.polink.user.dto.request.LoginRequest;
import com.polink.user.dto.request.SignupRequest;
import com.polink.user.dto.response.LoginResponse;
import com.polink.user.dto.response.SignupResponse;
import com.polink.user.entity.User;
import com.polink.user.service.JwtTokenService;
import com.polink.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    public AuthController(UserService userService, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
    }

    // 회원가입 요청을 처리한 후 새 회원의 번호와 아이디를 반환
    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request) {

        SignupResponse response = userService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 아이디·비밀번호가 일치 여부 확인
    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        User user = userService.authenticate(request);
        String accessToken = jwtTokenService.createAccessToken(user);

        LoginResponse response = new LoginResponse(
                user.getUserId(),
                user.getUserName(),
                user.getName(),
                user.getRole(),
                accessToken
        );

        return ResponseEntity.ok(response);
    }
}