package com.polink.investigation.controller;

import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.dto.response.WitnessListResponse;
import com.polink.investigation.service.WitnessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/witnesses")
public class WitnessController {

    private final WitnessService witnessService;

    public WitnessController(WitnessService witnessService) {
        this.witnessService = witnessService;
    }

    // 선택한 사건에 목격자와 최초 진술을 함께 등록
    @PostMapping
    public ResponseEntity<WitnessCreateResponse> createWitness(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WitnessCreateRequest request
    ) {

        // 목격자와 최초 진술 등록 후 응답 정보 받기
        WitnessCreateResponse response =
                witnessService.createWitness(
                        caseId,
                        toLoggedInUserId(jwt),
                        request
                );

        // 등록된 정보와 HTTP 201 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 사건별 목격자 정보와 진술 이력 조회
    @GetMapping
    public ResponseEntity<List<WitnessListResponse>> findWitnessList(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // 선택한 사건의 목격자·진술 목록 조회
        List<WitnessListResponse> responses =
                witnessService.findWitnessList(
                        caseId,
                        toLoggedInUserId(jwt)
                );

        // 조회 결과와 HTTP 200 반환
        return ResponseEntity.ok(responses);
    }

    // 검증된 JWT에서 회원 번호 추출 — 토큰이 없으면 Service에서 401 처리
    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}