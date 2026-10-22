package com.polink.user.controller;

import com.polink.user.dto.response.CitizenMyPageResponse;
import com.polink.user.service.CitizenMyPageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users/me")
public class CitizenMyPageController {

    private final CitizenMyPageService citizenMyPageService;

    public CitizenMyPageController(CitizenMyPageService citizenMyPageService) {
        this.citizenMyPageService = citizenMyPageService;
    }

    // 로그인한 시민 본인의 마이페이지 조회
    @GetMapping("/mypage")
    public ResponseEntity<CitizenMyPageResponse> getMyPage(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Integer loggedInUserId = requireLoggedInUserId(jwt);

        // 로그인한 회원 번호로 마이페이지 정보를 조회
        CitizenMyPageResponse response =
                citizenMyPageService.getCitizenMyPage(loggedInUserId);

        return ResponseEntity.ok(response);
    }

    // 검증된 JWT의 subject를 회원 번호로 변환
    private Integer requireLoggedInUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        try {
            return Integer.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 올바르지 않습니다."
            );
        }
    }
}