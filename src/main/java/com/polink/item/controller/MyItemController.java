package com.polink.item.controller;

import com.polink.item.dto.ItemPageResponse;
import com.polink.item.service.ItemService;
import com.polink.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * JWT로 로그인한 시민이 본인이 등록한 물품 목록을 조회하는 Controller다.
 * 이전의 local 프로필·app.dev-user-id 방식은 사용하지 않는다.
 * Authorization: Bearer {accessToken}의 JWT subject를 현재 사용자 ID로 사용한다.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class MyItemController {

    // 내 등록 물품 조회의 페이지·등록자 조건 검증과 DB 조회를 담당한다.
    private final ItemService itemService;

    /*
     * GET /api/v1/items/me?page=0&size=10
     * JWT로 로그인한 시민 본인이 등록한 물품만 최신 등록순으로 조회한다.
     */
    @GetMapping("/me")
    public ResponseEntity<ItemPageResponse> getMyItems(
            // Authorization 헤더의 Bearer 토큰에서 Spring Security가 추출한 JWT다.
            @AuthenticationPrincipal Jwt jwt,

            // page는 0부터 시작하며 기본값은 첫 번째 페이지인 0이다.
            @RequestParam(defaultValue = "0") int page,

            // size는 한 페이지에서 조회할 물품 수이며 기본값은 10이다.
            @RequestParam(defaultValue = "10") int size
    ) {
        // JWT subject(sub)에서 로그인한 사용자 ID를 가져온다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 시민 계정만 자신의 물품 목록을 조회할 수 있다.
        requireCitizen(jwt);

        // 요청에서 registrantId를 받지 않고 JWT 사용자 ID만 사용한다.
        // 따라서 다른 사용자의 등록 물품을 임의로 조회할 수 없다.
        return ResponseEntity.ok(
                itemService.getMyItems(currentUserId, page, size)
        );
    }

    /*
     * JWT의 subject(sub)를 현재 로그인한 회원 번호로 변환한다.
     * 토큰이 없거나 회원 번호 형식이 잘못되면 401 Unauthorized를 반환한다.
     */
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

    /*
     * JWT의 role Claim이 시민(CITIZEN)인지 확인한다.
     * 경찰·관리자 계정은 이 기능을 사용할 수 없으므로 403 Forbidden을 반환한다.
     */
    private void requireCitizen(Jwt jwt) {
        String roleName = jwt.getClaimAsString("role");

        if (!UserRole.CITIZEN.name().equals(roleName)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }
}