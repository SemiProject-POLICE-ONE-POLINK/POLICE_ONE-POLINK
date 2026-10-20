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
import com.polink.item.dev.LocalCurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 분실물·습득물 등록 요청을 받는 Controller다.
 * Controller는 HTTP 요청을 받고, 실제 등록 규칙은 ItemService에 맡긴다.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    /*
     * local 프로필에서만 LocalCurrentUserProvider Bean이 만들어진다.
     * ObjectProvider를 쓰면 local이 아닌 환경에서도 애플리케이션이 시작된다.
     */
    private final ObjectProvider<LocalCurrentUserProvider> localCurrentUserProvider;

    /**
     * GET /api/v1/items?page=0&size=10
     * <p>
     * 검색 조건은 모두 선택값이다.
     * 조건을 생략하면 전체 물품을 최신 등록순으로 페이지 단위 조회한다.
     */
    @GetMapping
    public ResponseEntity<ItemPageResponse> getItems(
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ItemStatus status,

            // 첫 페이지는 0번이며, 값을 생략하면 첫 페이지를 조회한다.
            @RequestParam(defaultValue = "0") int page,

            // 한 페이지의 기본 조회 개수는 10개다.
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                itemService.getItems(itemType, category, keyword, status, page, size)
        );
    }

    /**
     * POST /api/v1/items
     * multipart/form-data의 일반 입력값을 ItemCreateRequest에 연결한다.
     * image는 선택 이미지 파일 한 개다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemCreateResponse> registerItem(
            @Valid @ModelAttribute ItemCreateRequest request,
            @RequestPart(value = "image", required = false) List<MultipartFile> images
    ) {
        // 이미지가 두 개 이상이면 명세의 “이미지 1개” 조건에 맞지 않는다.
        if (images != null && images.size() > 1) {
            throw new IllegalArgumentException("이미지는 1개만 첨부할 수 있습니다.");
        }

        // 이미지가 없으면 null, 있으면 첫 번째 파일 한 개만 Service에 전달한다.
        MultipartFile image = images == null || images.isEmpty() ? null : images.get(0);

        // 현재는 실제 로그인 대신 application.yaml의 app.dev-user-id를 사용한다.
        LocalCurrentUserProvider currentUserProvider =
                localCurrentUserProvider.getIfAvailable();

        // local 프로필이 아니면 임시 사용자 제공자가 없으므로 등록을 막는다.
        if (currentUserProvider == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "로그인 기능이 아직 연결되지 않았습니다."
            );
        }

        // 시민 계정만 분실물·습득물 등록을 할 수 있다.
        currentUserProvider.requireCitizen();

        ItemCreateResponse response = itemService.registerItem(
                request,
                currentUserProvider.getCurrentUserId(),
                image
        );

        // 등록 성공 시 201과 새 물품의 상세 주소를 반환한다.
        return ResponseEntity
                .created(URI.create("/api/v1/items/" + response.itemId()))
                .body(response);
    }

    /**
     * GET /api/v1/items/{itemId}
     * 물품 번호는 분실물 L-0001 또는 습득물 F-0001 형식만 허용한다.
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<ItemDetailResponse> getItemDetail(
            @PathVariable String itemId
    ) {
        // 형식이 틀린 요청은 존재하지 않는 물품과 구분하여 400으로 처리한다.
        if (!itemId.matches("^[LF]-\\d{4}$")) {
            throw new IllegalArgumentException(
                    "물품 번호는 L-0001 또는 F-0001 형식이어야 합니다."
            );
        }

        // 형식은 맞지만 DB에 없는 물품이면 Service에서 404를 반환한다.
        return ResponseEntity.ok(itemService.getItemDetail(itemId));
    }

}