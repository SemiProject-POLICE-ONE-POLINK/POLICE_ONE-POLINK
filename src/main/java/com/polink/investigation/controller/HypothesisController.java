package com.polink.investigation.controller;

import com.polink.investigation.dto.request.HypothesisSaveRequest;
import com.polink.investigation.dto.response.HypothesisResponse;
import com.polink.investigation.service.HypothesisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/hypotheses")
public class HypothesisController {

    private final HypothesisService hypothesisService;

    public HypothesisController(HypothesisService hypothesisService) {
        this.hypothesisService = hypothesisService;
    }

    // 경찰관이 선택한 AI 가설 후보를 채택하여 저장
    @PostMapping
    public ResponseEntity<HypothesisResponse> adoptHypothesis(
            @PathVariable("caseId") String caseId,

            // 검증된 Bearer 토큰 — 토큰 없이 요청 시 null
            @AuthenticationPrincipal Jwt jwt,

            @Valid @RequestBody HypothesisSaveRequest request
    ) {

        // 토큰의 회원 번호(sub)를 로그인 사용자 번호로 변환 — 토큰이 없을 시 null을 넘기고 Service에서 401 처리
        Integer loggedInUserId =
                jwt == null ? null : Integer.valueOf(jwt.getSubject());
        HypothesisResponse response =
                hypothesisService.adoptHypothesis(
                        caseId,
                        loggedInUserId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
