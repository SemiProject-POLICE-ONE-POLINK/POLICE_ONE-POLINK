package com.polink.user.controller;

import com.polink.user.dto.response.AssignablePoliceListResponse;
import com.polink.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "배정 가능한 경찰관 목록 조회")
    @GetMapping("/police-officers")
    public ResponseEntity<AssignablePoliceListResponse>
    getAssignablePolice(
            @AuthenticationPrincipal Jwt jwt
    ) {
        AssignablePoliceListResponse response =
                adminUserService.getAssignablePolice(
                        toLoggedInUserId(jwt)
                );

        return ResponseEntity.ok(response);
    }

    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}