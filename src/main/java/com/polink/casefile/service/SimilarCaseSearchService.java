package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;
import com.polink.casefile.dto.request.SimilarCaseSearchRequest;
import com.polink.casefile.dto.response.SimilarCaseResponse;
import com.polink.casefile.dto.response.SimilarCaseSearchResponse;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.repository.CaseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SimilarCaseSearchService {

    private static final int CANDIDATE_LIMIT = 50;

    private final CaseRepository caseRepository;
    private final SimilarCaseAiClient similarCaseAiClient;

    public SimilarCaseSearchService(
            CaseRepository caseRepository,
            SimilarCaseAiClient similarCaseAiClient
    ) {
        this.caseRepository = caseRepository;
        this.similarCaseAiClient = similarCaseAiClient;
    }

    public SimilarCaseSearchResponse search(
            Integer userId,
            SimilarCaseSearchRequest request
    ) {
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        List<CaseFile> candidateCases = caseRepository.findAll(
                PageRequest.of(
                        0,
                        CANDIDATE_LIMIT,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).getContent();

        List<SimilarCaseAiInput.CaseCandidate> candidates =
                candidateCases.stream()
                        .map(this::toAiCandidate)
                        .toList();

        SimilarCaseAiResult aiResult = similarCaseAiClient.analyze(
                new SimilarCaseAiInput(
                        request.getSituation(),
                        candidates
                )
        );

        if (aiResult == null || aiResult.suggestedCaseType() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 분석 결과를 받지 못했습니다."
            );
        }

        Map<String, CaseFile> casesById = candidateCases.stream()
                .collect(Collectors.toMap(
                        CaseFile::getCaseId,
                        Function.identity()
                ));

        List<SimilarCaseResponse> similarCases = aiResult.matches() == null
                ? List.of()
                : aiResult.matches().stream()
                .filter(Objects::nonNull)
                .map(match -> toResponse(match, casesById))
                .filter(Objects::nonNull)
                .limit(3)
                .toList();

        return new SimilarCaseSearchResponse(
                aiResult.analysis(),
                aiResult.suggestedCaseType(),
                aiResult.recommendedAction(),
                similarCases
        );
    }

    private SimilarCaseAiInput.CaseCandidate toAiCandidate(
            CaseFile caseFile
    ) {
        return new SimilarCaseAiInput.CaseCandidate(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getCaseType(),
                caseFile.getDescription(),
                caseFile.getOccurredAt(),
                caseFile.getLocation()
        );
    }

    private SimilarCaseResponse toResponse(
            SimilarCaseAiResult.Match match,
            Map<String, CaseFile> casesById
    ) {
        CaseFile caseFile = casesById.get(match.caseId());

        if (caseFile == null) {
            return null;
        }

        return new SimilarCaseResponse(
                caseFile.getCaseId(),
                caseFile.getTitle(),
                caseFile.getCaseType(),
                match.similarityReason()
        );
    }
}
