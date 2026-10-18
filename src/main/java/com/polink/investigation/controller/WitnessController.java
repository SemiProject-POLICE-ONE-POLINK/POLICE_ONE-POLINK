package com.polink.investigation.controller;

import com.polink.investigation.dto.request.WitnessCreateRequest;
import com.polink.investigation.dto.response.WitnessCreateResponse;
import com.polink.investigation.service.WitnessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases/{caseId}/witnesses")
public class WitnessController {

    private final WitnessService witnessService;

    public WitnessController(WitnessService witnessService) {
        this.witnessService = witnessService;
    }

    // 선택한 사건에 목격자와 최초 진술을 함께 등록
    @PostMapping
    public ResponseEntity<WitnessCreateResponse> createWitness(
            @PathVariable("caseId") String caseId,
            @Valid @RequestBody WitnessCreateRequest request
    ) {

        // 목격자와 최초 진술 등록 후 응답 정보 받기
        WitnessCreateResponse response =
                witnessService.createWitness(caseId, request);

        // 등록된 정보와 HTTP 201 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
