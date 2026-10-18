package com.polink.item.controller;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.service.ItemService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분실물·습득물 등록 요청을 받는 Controller다.
 * Controller는 HTTP 요청을 받고, 실제 등록 규칙은 ItemService에 맡긴다.
 */
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /**
     * POST /api/items
     *
     * 요청 본문의 itemType이 LOST면 분실물, FOUND면 습득물로 등록한다.
     * @Valid는 ItemCreateRequest에 적은 필수 입력값 검사를 실행한다.
     */
    @PostMapping
    public ResponseEntity<ItemCreateResponse> registerItem(
            @Valid @RequestBody ItemCreateRequest request
    ) {
        ItemCreateResponse response = itemService.registerItem(request);

        // 새 물품이 생성되었으므로 HTTP 201(Created)과 새 물품의 주소를 함께 반환한다.
        return ResponseEntity
                .created(URI.create("/api/items/" + response.itemId()))
                .body(response);
    }
}
