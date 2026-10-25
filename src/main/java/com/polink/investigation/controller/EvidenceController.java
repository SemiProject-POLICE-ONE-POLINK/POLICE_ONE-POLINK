package com.polink.investigation.controller;

import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.dto.response.EvidenceFileResponse;
import com.polink.investigation.dto.response.EvidenceListResponse;
import com.polink.investigation.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "수사")
@RestController
@RequestMapping("/api/v1/cases/{caseId}/evidences")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    // 선택한 사건에 첨부파일을 포함한 증거물 등록
    @Operation(summary = "첨부파일 포함 증거물 등록")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceCreateResponse> createEvidenceWithFile(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("request") EvidenceCreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        // Service를 호출해 증거물 등록
        EvidenceCreateResponse response =
                evidenceService.createEvidence(
                        caseId,
                        toLoggedInUserId(jwt),
                        request,
                        file
                );

        // 등록된 증거물 정보와 HTTP 201 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 선택한 사건에 파일 없이 증거물 등록
    @Operation(summary = "증거물 등록")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvidenceCreateResponse> createEvidence(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EvidenceCreateRequest request
    ) {
        EvidenceCreateResponse response =
                evidenceService.createEvidence(
                        caseId,
                        toLoggedInUserId(jwt),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 선택한 사건의 증거물 목록 조회
    @Operation(summary = "증거물 목록 조회")
    @GetMapping
    public ResponseEntity<List<EvidenceListResponse>> findEvidenceList(
            @PathVariable("caseId") String caseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // Service에서 조회한 응답 DTO 목록 받기
        List<EvidenceListResponse> responses =
                evidenceService.findEvidenceList(
                        caseId,
                        toLoggedInUserId(jwt)
                );

        // HTTP 200과 증거물 목록 반환
        return ResponseEntity.ok(responses);
    }

    // 증거물 첨부파일 조회 — 이미지는 표시하고 나머지는 다운로드
    @Operation(summary = "증거물 첨부파일 조회")
    @GetMapping("/{evidenceId}/file")
    public ResponseEntity<byte[]> findEvidenceFile(
            @PathVariable("caseId") String caseId,
            @PathVariable("evidenceId") String evidenceId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // 1. 권한 확인 후 저장된 파일 내용 가져오기
        EvidenceFileResponse response =
                evidenceService.findEvidenceFile(
                        caseId,
                        evidenceId,
                        toLoggedInUserId(jwt)
                );

        // 2. 이미지는 바로 표시, 나머지 파일은 다운로드로 설정
        ContentDisposition disposition =
                (response.contentType().startsWith("image/")
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment())
                        .filename(
                                response.filename(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        // 3. JSON 대신 실제 파일 내용을 반환
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(response.contentType())
                )
                .contentLength(response.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                // 민감한 파일이 브라우저 캐시에 저장되지 않도록 설정
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-store"
                )
                // 지정한 파일 형식으로 처리하도록 설정
                .header("X-Content-Type-Options", "nosniff")
                .body(response.content());
    }

    // 검증된 JWT에서 회원 번호 추출 — 토큰이 없으면 Service에서 401 처리
    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}
