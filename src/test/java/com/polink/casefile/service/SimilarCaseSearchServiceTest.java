package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;
import com.polink.casefile.dto.request.SimilarCaseSearchRequest;
import com.polink.casefile.dto.response.SimilarCaseSearchResponse;
import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseType;
import com.polink.casefile.repository.CaseRepository;
import com.polink.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarCaseSearchServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private SimilarCaseAiClient similarCaseAiClient;

    private SimilarCaseSearchService similarCaseSearchService;

    @BeforeEach
    void setUp() {
        similarCaseSearchService = new SimilarCaseSearchService(
                caseRepository,
                similarCaseAiClient
        );
    }

    @Test
    void search_success() {
        SimilarCaseSearchRequest request = request(
                "중고 거래 대금을 송금했지만 연락이 끊겼습니다."
        );

        CaseFile fraudCase = caseFile(
                "SMPA-2026-0001",
                "중고 거래 사기",
                CaseType.FRAUD
        );

        CaseFile theftCase = caseFile(
                "SMPA-2026-0002",
                "지갑 도난",
                CaseType.THEFT
        );

        when(caseRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fraudCase, theftCase)));

        when(similarCaseAiClient.analyze(any(SimilarCaseAiInput.class)))
                .thenReturn(new SimilarCaseAiResult(
                        "사기 의심 상황입니다.",
                        CaseType.FRAUD,
                        "송금 내역을 보관해 주세요.",
                        List.of(
                                new SimilarCaseAiResult.Match(
                                        "SMPA-2026-0001",
                                        "송금 후 연락이 끊긴 상황이 유사합니다."
                                ),
                                new SimilarCaseAiResult.Match(
                                        "NOT-EXIST",
                                        "존재하지 않는 사건입니다."
                                )
                        )
                ));

        SimilarCaseSearchResponse response =
                similarCaseSearchService.search(1, request);

        assertEquals("사기 의심 상황입니다.", response.getAnalysis());
        assertEquals(CaseType.FRAUD, response.getSuggestedCaseType());
        assertEquals(1, response.getSimilarCases().size());
        assertEquals(
                "SMPA-2026-0001",
                response.getSimilarCases().get(0).getCaseId()
        );

        ArgumentCaptor<SimilarCaseAiInput> inputCaptor =
                ArgumentCaptor.forClass(SimilarCaseAiInput.class);

        verify(similarCaseAiClient).analyze(inputCaptor.capture());

        assertEquals(
                request.getSituation(),
                inputCaptor.getValue().situation()
        );
        assertEquals(2, inputCaptor.getValue().candidates().size());
    }

    @Test
    void search_withoutUser_throwsUnauthorized() {
        SimilarCaseSearchRequest request = request("테스트 상황");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> similarCaseSearchService.search(null, request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(caseRepository, similarCaseAiClient);
    }

    @Test
    void search_withoutAiResult_throwsBadGateway() {
        SimilarCaseSearchRequest request = request("테스트 상황");

        when(caseRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<CaseFile>(List.of()));

        when(similarCaseAiClient.analyze(any(SimilarCaseAiInput.class)))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> similarCaseSearchService.search(1, request)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }

    private SimilarCaseSearchRequest request(String situation) {
        SimilarCaseSearchRequest request =
                new SimilarCaseSearchRequest();

        ReflectionTestUtils.setField(
                request,
                "situation",
                situation
        );

        return request;
    }

    private CaseFile caseFile(
            String caseId,
            String title,
            CaseType caseType
    ) {
        User reporter = new User(
                "citizen01",
                "test-hash",
                "테스트시민",
                "010-0000-0000"
        );

        return new CaseFile(
                caseId,
                reporter,
                title,
                caseType,
                "테스트 사건 내용",
                LocalDateTime.of(2026, 10, 22, 10, 0),
                "테스트 장소"
        );
    }
}
