package com.polink.casefile.controller;

import com.polink.casefile.dto.request.SimilarCaseSearchRequest;
import com.polink.casefile.dto.response.SimilarCaseSearchResponse;
import com.polink.casefile.service.SimilarCaseSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseAiSearchController {

    private final SimilarCaseSearchService similarCaseSearchService;

    public CaseAiSearchController(
            SimilarCaseSearchService similarCaseSearchService
    ) {
        this.similarCaseSearchService = similarCaseSearchService;
    }

    @PostMapping("/ai-search")
    public ResponseEntity<SimilarCaseSearchResponse> searchSimilarCases(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SimilarCaseSearchRequest request
    ) {
        SimilarCaseSearchResponse response =
                similarCaseSearchService.search(
                        toLoggedInUserId(jwt),
                        request
                );

        return ResponseEntity.ok(response);
    }

    private Integer toLoggedInUserId(Jwt jwt) {
        return jwt == null
                ? null
                : Integer.valueOf(jwt.getSubject());
    }
}
