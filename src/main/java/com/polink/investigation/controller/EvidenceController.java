package com.polink.investigation.controller;

import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.service.EvidenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases/{caseId}/evidences")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    // 선택한 사건에 첨부파일을 포함한 증거물 등록
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenceCreateResponse> createEvidenceWithFile(
            @PathVariable("caseId") String caseId,
            @Valid @RequestPart("request") EvidenceCreateRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ){
      // Service를 호출해 증거물 등록
      EvidenceCreateResponse response = evidenceService.createEvidence(caseId, request, file);

      // 등록된 증거물 정보와 HTTP 201 반환
      return ResponseEntity
             .status(HttpStatus.CREATED)
             .body(response);
    }

    // 선택한 사건에 파일 없이 증거물 등록
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvidenceCreateResponse> createEvidence(
            @PathVariable("caseId") String caseId,
            @Valid @RequestBody EvidenceCreateRequest request
    ) {
        EvidenceCreateResponse response =
                evidenceService.createEvidence(caseId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
