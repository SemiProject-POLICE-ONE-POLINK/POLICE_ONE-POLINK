package com.polink.item.controller;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.service.ItemService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.polink.item.dto.ItemPageResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.polink.item.dto.ItemUpdateRequest;
import com.polink.user.entity.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.polink.item.dto.ItemStatisticsResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/*
 * 분실물·습득물 등록 요청을 받는 Controller다.
 * Controller는 HTTP 요청을 받고, 실제 등록 규칙은 ItemService에 맡긴다.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    // 물품 등록·수정·이미지 첨부 등의 실제 업무 규칙을 처리한다.
    private final ItemService itemService;
    /*
     * GET /api/v1/items?page=0&size=10
     * <p>
     * 검색 조건은 모두 선택값이다.
     * 조건을 생략하면 전체 물품을 최신 등록순으로 페이지 단위 조회한다.
     */
    @GetMapping
    public ResponseEntity<ItemPageResponse> getItems(
            // Authorization: Bearer 토큰에서 검증된 로그인 사용자 정보다.
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,

            // 발생 장소를 일부만 입력해도 포함된 물품을 검색한다.
            @RequestParam(required = false) String location,

            // yyyy-MM-dd 형식의 발생일 범위다. 둘 다 선택값이다.
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false) ItemStatus status,
            // 첫 페이지는 0번이며, 값을 생략하면 첫 페이지를 조회한다.
            @RequestParam(defaultValue = "0") int page,

            // 한 페이지의 기본 조회 개수는 10개다.
            @RequestParam(defaultValue = "10") int size
    ) {
        // 목록 조회는 시민·경찰·관리자 모두 가능하다.
        // 단, 로그인하지 않았거나 JWT의 사용자 식별값(sub)이 없으면 401을 반환한다.
        requireLoggedInUserId(jwt);

        return ResponseEntity.ok(
                itemService.getItems(
                        itemType,
                        category,
                        keyword,
                        location,
                        status,
                        startDate,
                        endDate,
                        page,
                        size
                )
        );
    }

    /*
     * POST /api/v1/items
     * multipart/form-data의 일반 입력값을 ItemCreateRequest에 연결한다.
     * image는 선택 이미지 파일 한 개다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemCreateResponse> registerItem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute ItemCreateRequest request,
            @RequestPart(value = "image", required = false) List<MultipartFile> images
    ) {
        // 이미지가 두 개 이상이면 명세의 “이미지 1개” 조건에 맞지 않는다.
        if (images != null && images.size() > 1) {
            throw new IllegalArgumentException("이미지는 1개만 첨부할 수 있습니다.");
        }

        // 이미지가 없으면 null, 있으면 첫 번째 파일 한 개만 Service에 전달한다.
        MultipartFile image = images == null || images.isEmpty() ? null : images.get(0);

        // JWT 토큰의 sub 값을 현재 로그인한 사용자 ID로 사용한다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 물품 등록은 시민 계정만 가능하다.
        requireCitizen(jwt);

        ItemCreateResponse response = itemService.registerItem(
                request,
                currentUserId,
                image
        );

        // 등록 성공 시 201과 새 물품의 상세 주소를 반환한다.
        return ResponseEntity
                .created(URI.create("/api/v1/items/" + response.itemId()))
                .body(response);
    }

    /*
     * GET /api/v1/items/{itemId}
     * 물품 번호는 분실물 L-0001 또는 습득물 F-0001 형식만 허용한다.
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<ItemDetailResponse> getItemDetail(
            // Authorization: Bearer 토큰에서 검증된 로그인 사용자 정보다.
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String itemId
    ) {
        // 상세 조회는 시민·경찰·관리자 모두 가능하다.
        // 토큰이 없거나 JWT의 sub Claim이 비정상이면 401을 반환한다.
        requireLoggedInUserId(jwt);

        // 형식이 틀린 요청은 존재하지 않는 물품과 구분하여 400으로 처리한다.
        if (!itemId.matches("^[LF]-\\d{4}$")) {
            throw new IllegalArgumentException(
                    "물품 번호는 L-0001 또는 F-0001 형식이어야 합니다."
            );
        }

        // 형식은 맞지만 DB에 없는 물품이면 Service에서 404를 반환한다.
        return ResponseEntity.ok(itemService.getItemDetail(itemId));
    }

    /*
     * PATCH /api/v1/items/{itemId}
     * JWT의 role Claim에 따라 수정 가능한 물품 종류를 구분한다.
     * 시민(CITIZEN): 본인이 등록했고 분실 중(LOST)인 분실물만 수정 가능
     * 경찰(POLICE): 보관 중(STORED)인 습득물만 수정 가능
     * 관리자(ADMIN): 물품 수정 기능의 대상 역할이 아니므로 접근 불가
     */
    @PatchMapping(
            value = "/{itemId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ItemDetailResponse> updateItem(
            // Authorization: Bearer {token}에서 검증된 JWT를 Spring Security가 주입한다.
            @AuthenticationPrincipal Jwt jwt,

            @PathVariable String itemId,

            // 요청 본문의 필수값·날짜 검증을 먼저 수행한다.
            @Valid @RequestBody ItemUpdateRequest request
    ) {
        // JWT의 sub Claim을 로그인한 사용자 ID로 변환한다.
        // 토큰이 없거나 sub 값이 비정상이면 공통 메서드가 401을 반환한다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // JWT의 role Claim을 CITIZEN, POLICE, ADMIN enum으로 변환한다.
        UserRole currentUserRole = requireLoggedInUserRole(jwt);

        // 역할마다 호출할 Service와 수정 가능한 물품 종류를 명확히 구분한다.
        ItemDetailResponse response = switch (currentUserRole) {
            // 시민의 경우 Service가 등록자 일치 여부와 LOST 상태를 추가 검증한다.
            case CITIZEN ->
                    itemService.updateLostItem(itemId, currentUserId, request);

            // 경찰의 경우 Service가 FOUND 유형과 STORED 상태를 추가 검증한다.
            case POLICE ->
                    itemService.updateFoundItem(itemId, currentUserId, request);

            // 관리자는 통계 등 별도 관리 기능의 대상이며 물품 정보 수정은 할 수 없다.
            case ADMIN -> throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 또는 경찰 계정만 물품 정보를 수정할 수 있습니다."
            );
        };

        // 수정된 물품 상세 정보를 200 OK와 함께 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * POST /api/v1/items/{itemId}/image
     * 로그인한 시민이 본인이 등록한 분실물에 이미지 한 장을 첨부한다.
     * 파일은 multipart/form-data의 image 필드로 전달한다.
     * 실제 등록자·물품 유형·상태·중복 이미지 검증은 Service가 담당한다.
     */
    @PostMapping(
            value = "/{itemId}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ItemDetailResponse> attachLostItemImage(
            // Authorization 헤더의 Bearer 토큰에서 추출된 JWT다.
            @AuthenticationPrincipal Jwt jwt,

            // 이미지를 연결할 대상 분실물 번호다.
            @PathVariable String itemId,

            // 하나의 image 필드로 전달된 업로드 파일 목록이다.
            @RequestPart("image") List<MultipartFile> images
    ) {
        // 요구사항상 분실물 한 건에는 이미지 한 장만 요청으로 전달할 수 있다.
        if (images == null || images.size() != 1) {
            throw new IllegalArgumentException("이미지는 1개만 첨부할 수 있습니다.");
        }

        // 토큰의 sub에서 로그인한 시민의 회원 번호를 가져온다.
        Integer currentUserId = requireLoggedInUserId(jwt);

        // 이미지 첨부는 시민 계정만 가능하다.
        // 경찰·관리자 계정은 여기서 403 Forbidden으로 차단한다.
        requireCitizen(jwt);

        // Service가 본인 물품 여부, LOST 상태, 기존 이미지 존재 여부를 검사하고
        // ItemImageService를 통해 실제 파일 저장과 imagePath 연결을 처리한다.
        ItemDetailResponse response = itemService.attachLostItemImage(
                itemId,
                currentUserId,
                images.get(0)
        );

        // 파일 경로가 포함된 수정 후 물품 상세 정보를 200 OK로 반환한다.
        return ResponseEntity.ok(response);
    }

    /*
     * Authorization Bearer 토큰에서 로그인 회원 번호(sub)를 추출한다.
     * 토큰이 없거나 회원 번호 형식이 올바르지 않으면 401을 반환한다.
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
     * JWT의 role Claim을 UserRole enum으로 변환한다.
     * 토큰에 역할 정보가 없거나 지원하지 않는 역할이면 401을 반환한다.
     */
    private UserRole requireLoggedInUserRole(Jwt jwt) {
        // 토큰 자체와 sub 값이 유효한지 먼저 확인한다.
        requireLoggedInUserId(jwt);

        String roleName = jwt.getClaimAsString("role");
        if (roleName == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 역할 정보가 없습니다."
            );
        }

        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 역할 정보가 올바르지 않습니다."
            );
        }
    }

    /** 시민 계정만 사용할 수 있는 기능의 권한을 검사한다. */
    private void requireCitizen(Jwt jwt) {
        if (requireLoggedInUserRole(jwt) != UserRole.CITIZEN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "시민 계정만 사용할 수 있는 기능입니다."
            );
        }
    }

    /*
     * GET /api/v1/items/statistics
     * 경찰 계정이 전체 분실물·습득물 현황 통계를 조회한다.
     */
    @GetMapping("/statistics")
    public ResponseEntity<ItemStatisticsResponse> getItemStatistics(
            @AuthenticationPrincipal Jwt jwt
    ) {
        // 토큰 유효성 검증과 역할 추출을 함께 수행한다.
        if (requireLoggedInUserRole(jwt) != UserRole.POLICE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "경찰 계정만 물품 통계를 조회할 수 있습니다."
            );
        }

        return ResponseEntity.ok(itemService.getItemStatistics());
    }
}