package com.polink.item.controller;

import com.polink.item.dev.LocalCurrentUserProvider;
import com.polink.item.dto.ItemPageResponse;
import com.polink.item.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실제 로그인 기능이 완성되기 전,
 * 더미 사용자로 내 등록 물품을 조회하는 로컬 전용 Controller다.
 */
@RestController
@Profile("local")
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class LocalMyItemController {

    private final ItemService itemService;
    private final LocalCurrentUserProvider currentUserProvider;

    /**
     * GET /api/v1/items/me?page=0&size=10
     * 현재 더미 시민 사용자가 등록한 물품만 조회한다.
     */
    @GetMapping("/me")
    public ResponseEntity<ItemPageResponse> getMyItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        currentUserProvider.requireCitizen();

        return ResponseEntity.ok(
                itemService.getMyItems(
                        currentUserProvider.getCurrentUserId(),
                        page,
                        size
                )
        );
    }
}
