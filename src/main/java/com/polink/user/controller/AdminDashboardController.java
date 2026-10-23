package com.polink.user.controller;

import com.polink.user.dto.response.AdminDashboardResponse;
import com.polink.user.service.AdminDashboardService;
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
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    // 로그인한 관리자의 대시보드 조회
    @Operation(summary = "관리자 전용 대시보드 조회")
    @GetMapping("/admin-dashboard")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboard(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Integer loggedInUserId = requireLoggedInUserId(jwt);

        AdminDashboardResponse response =
                adminDashboardService.getAdminDashboard(loggedInUserId);

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