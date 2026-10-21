package com.polink.casefile.controller;

import com.polink.casefile.dto.request.CaseCloseRequest;
import com.polink.casefile.dto.request.CaseCreateRequest;
import com.polink.casefile.dto.request.CaseOfficerRequest;
import com.polink.casefile.dto.response.CaseCloseResponse;
import com.polink.casefile.dto.response.CaseCreateResponse;
import com.polink.casefile.dto.response.CaseDetailResponse;
import com.polink.casefile.dto.response.CaseListResponse;
import com.polink.casefile.dto.response.CaseOfficerResponse;
import com.polink.casefile.dto.response.MyCaseListResponse;
import com.polink.casefile.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    public ResponseEntity<CaseCreateResponse> createCase(
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer reporterId,
            @Valid @RequestBody CaseCreateRequest request
    ) {
        CaseCreateResponse response =
                caseService.createCase(reporterId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<CaseListResponse> getCases(
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer userId,
            Pageable pageable
    ) {
        CaseListResponse response =
                caseService.getCases(userId, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<MyCaseListResponse> getMyCases(
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer userId,
            Pageable pageable
    ) {
        MyCaseListResponse response =
                caseService.getMyCases(userId, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<CaseDetailResponse> getCaseDetail(
            @PathVariable("caseId") String caseId,
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer userId
    ) {
        CaseDetailResponse response =
                caseService.getCaseDetail(userId, caseId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{caseId}/officer")
    public ResponseEntity<CaseOfficerResponse> assignOfficer(
            @PathVariable("caseId") String caseId,
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer adminId,
            @Valid @RequestBody CaseOfficerRequest request
    ) {
        CaseOfficerResponse response =
                caseService.assignOfficer(adminId, caseId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{caseId}/close")
    public ResponseEntity<CaseCloseResponse> closeCase(
            @PathVariable("caseId") String caseId,
            // JWT 연동 전 개발 테스트용 헤더. 인증 연동 시 교체한다.
            @RequestHeader(value = "X-User-Id", required = false)
            Integer adminId,
            @Valid @RequestBody CaseCloseRequest request
    ) {
        CaseCloseResponse response =
                caseService.closeCase(adminId, caseId, request);

        return ResponseEntity.ok(response);
    }
}
