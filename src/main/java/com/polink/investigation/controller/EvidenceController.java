package com.polink.investigation.controller;

import com.polink.investigation.dto.request.EvidenceCreateRequest;
import com.polink.investigation.dto.response.EvidenceCreateResponse;
import com.polink.investigation.service.EvidenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases/{caseId}/evidences")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    // 선택한 사건에 증거물 등록
    @PostMapping
    public ResponseEntity<EvidenceCreateResponse> createEvidence(
            @PathVariable("caseId") String caseId,
            @Valid @RequestBody EvidenceCreateRequest request
    ){
      // Service를 호출해 증거물 등록
      EvidenceCreateResponse response = evidenceService.createEvidence(caseId, request);

      // 등록된 증거물 정보와 HTTP 201 반환
      return ResponseEntity
             .status(HttpStatus.CREATED)
             .body(response);
    }
}
