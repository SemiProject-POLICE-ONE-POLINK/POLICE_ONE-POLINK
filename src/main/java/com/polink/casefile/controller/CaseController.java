package com.polink.casefile.controller;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.request.CaseOfficerRequest;
import com.polink.casefile.dto.response.*;
import com.polink.casefile.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사건")
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @Operation(summary = "사건 접수")
    @PostMapping
    public ResponseEntity<CaseCreateResponse> createCase(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CaseCreateRequest request
    ) {
        CaseCreateResponse response =
                caseService.createCase(toLoggedInUserId(jwt), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "사건 목록 조회")
    @GetMapping
    public ResponseEntity<CaseListResponse> getCases(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        CaseListResponse response =
                caseService.getCases(toLoggedInUserId(jwt), pageable);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 사건 조회")
    @GetMapping("/my")
    public ResponseEntity<MyCaseListResponse> getMyCases(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        MyCaseListResponse response =
                caseService.getMyCases(toLoggedInUserId(jwt), pageable);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사건 상세 조회")
    @GetMapping("/{caseId}")
    public ResponseEntity<CaseDetailResponse> getCaseDetail(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        CaseDetailResponse response =
                caseService.getCaseDetail(toLoggedInUserId(jwt), caseId);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "담당 경찰 배정")
    @PatchMapping("/{caseId}/officer")
    public ResponseEntity<CaseOfficerResponse> assignOfficer(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CaseOfficerRequest request
    ) {
        CaseOfficerResponse response =
                caseService.assignOfficer(toLoggedInUserId(jwt), caseId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "사건 종결")
    @PostMapping("/{caseId}/close")
    public ResponseEntity<CaseCloseResponse> closeCase(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CaseCloseRequest request
    ) {
        CaseCloseResponse response =
                caseService.closeCase(toLoggedInUserId(jwt), caseId, request);

        return ResponseEntity.ok(response);
    }

    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}
