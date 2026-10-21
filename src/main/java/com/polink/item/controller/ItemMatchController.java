package com.polink.item.controller;

import com.polink.item.dto.ItemMatchListResponse;
import com.polink.item.dto.ItemMatchRequest;
import com.polink.item.service.ItemMatchService;
import com.polink.user.entity.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/*
 * 로그인한 시민이 본인이 등록한 분실물을 기준으로
 * 습득물 후보 매칭을 요청하는 Controller다.
 */
@RestController
@RequestMapping("/api/v1/ai/item-matches")
@RequiredArgsConstructor
public class ItemMatchController {

    // 카테고리·물품명·장소를 비교해 습득물 후보를 찾는 업무 로직을 담당한다.
    private final ItemMatchService itemMatchService;

    /*
     * POST /api/v1/ai/item-matches
     * 요청한 분실물이 로그인한 시민의 물품인지 확인한 뒤
     * 보관 중인 습득물 후보와 매칭 근거를 반환한다.
     */
    @PostMapping
    public ResponseEntity<ItemMatchListResponse> findMatches(
            // Authorization: Bearer 토큰에서 Spring Security가 추출한 JWT다.
            @AuthenticationPrincipal Jwt jwt,

            // lostItemId는 비어 있을 수 없으며, @Valid가 요청값을 검사한다.
            @Valid @RequestBody ItemMatchRequest request
    ) {
        // JWT subject(sub)에서 현재 로그인한 시민의 회원 번호를 가져온다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 물품 매칭은 시민이 본인 분실물을 찾을 때만 사용할 수 있다.
        requireCitizen(jwt);

        // Service가 소유권·분실물 상태·후보 유사도 계산을 처리한다.
        ItemMatchListResponse response = itemMatchService.findMatches(
                currentUserId,
                request.lostItemId()
        );

        return ResponseEntity.ok(response);
    }

    /*
     * JWT subject(sub)를 로그인한 회원 번호로 변환한다.
     * 토큰이 없거나 회원 번호 형식이 잘못되면 401을 반환한다.
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
     * JWT role Claim이 시민(CITIZEN)인지 확인한다.
     * 경찰·관리자 계정은 다른 사람의 물품 매칭을 요청할 수 없다.
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