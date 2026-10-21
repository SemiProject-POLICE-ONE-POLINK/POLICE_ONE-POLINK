package com.polink.investigation.controller;

import com.polink.investigation.dto.request.HypothesisSaveRequest;
import com.polink.investigation.dto.response.HypothesisGenerateResponse;
import com.polink.investigation.dto.response.HypothesisResponse;
import com.polink.investigation.service.HypothesisGenerationService;
import com.polink.investigation.service.HypothesisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/hypotheses")
public class HypothesisController {

    // 선택한 가설의 채택·저장
    private final HypothesisService hypothesisService;

    // 사건 자료 기반 가설 후보 생성
    private final HypothesisGenerationService hypothesisGenerationService;

    public HypothesisController(HypothesisService hypothesisService, HypothesisGenerationService hypothesisGenerationService) {
        this.hypothesisService = hypothesisService;
        this.hypothesisGenerationService = hypothesisGenerationService;
    }

    // 가설 후보 생성 — 생성 결과는 DB에 저장하지 않음(가짜 후보로 테스트)
// POST /api/v1/cases/{caseId}/hypotheses/generate
    @PostMapping("/generate")
    public ResponseEntity<HypothesisGenerateResponse> generateHypotheses(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // 검증된 토큰에서 로그인 회원 번호 확인
        Integer loggedInUserId =
                jwt == null ? null : Integer.valueOf(jwt.getSubject());

        // 사건 자료를 조회해 가설 후보 생성
        HypothesisGenerateResponse response =
                hypothesisGenerationService.generateHypotheses(
                        caseId,
                        loggedInUserId
                );

        // 생성된 후보와 HTTP 200 반환
        return ResponseEntity.ok(response);
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

    // 담당 경찰이 사건에 저장된 가설 목록을 조회
    @GetMapping
    public ResponseEntity<List<HypothesisResponse>> getAdoptedHypotheses(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Integer loggedInUserId =
                jwt == null ? null : Integer.valueOf(jwt.getSubject());

        List<HypothesisResponse> responses =
                hypothesisService.findAdoptedHypotheses(
                        caseId,
                        loggedInUserId
                );

        return ResponseEntity.ok(responses);
    }
}
