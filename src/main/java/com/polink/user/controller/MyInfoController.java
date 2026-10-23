package com.polink.user.controller;

import com.polink.user.dto.response.MyInfoResponse;
import com.polink.user.service.MyInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users/me")
public class MyInfoController {

    private final MyInfoService myInfoService;

    public MyInfoController(MyInfoService myInfoService) {
        this.myInfoService = myInfoService;
    }

    // 로그인한 회원 본인의 정보 조회
    @Operation(summary = "내 정보 조회")
    @GetMapping
    public ResponseEntity<MyInfoResponse> findMyInfo(
            @AuthenticationPrincipal Jwt jwt
    ) {
        MyInfoResponse response = myInfoService.findMyInfo(
                toLoggedInUserId(jwt)
        );

        return ResponseEntity.ok(response);
    }

    // 검증된 JWT에서 회원 번호 추출
    private Integer toLoggedInUserId(Jwt jwt) {

        // 토큰이 없으면 Service에서 401 처리
        if (jwt == null) {
            return null;
        }

        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw invalidLoginInfo();
        }

        try {
            return Integer.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw invalidLoginInfo();
        }
    }

    // 회원 번호를 확인할 수 없는 토큰은 인증 오류로 처리
    private ResponseStatusException invalidLoginInfo() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인 정보를 확인할 수 없습니다. 다시 로그인해주세요."
        );
    }
}
