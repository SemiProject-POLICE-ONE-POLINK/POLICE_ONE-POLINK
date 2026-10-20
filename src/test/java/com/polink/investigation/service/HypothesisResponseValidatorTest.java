package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.dto.response.HypothesisCandidateResponse;
import com.polink.investigation.entity.StatementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// DB·AI 호출 없이 가설 응답 검증 로직만 검사
class HypothesisResponseValidatorTest {

    private final HypothesisResponseValidator validator =
            new HypothesisResponseValidator();

    private static final String EVIDENCE_ID = "EV-2026-0405-01";
    private static final Integer STATEMENT_ID = 7;

    // AI에 전달한 자료 역할의 테스트 DTO
    private final HypothesisAiInput input = new HypothesisAiInput(
            "SMPA-2026-0405",
            "테스트 사건",
            "가상 사건 내용입니다.",
            List.of(
                    new HypothesisAiInput.EvidenceInput(
                            EVIDENCE_ID,
                            "현장 사진",
                            "출입구에 신발 자국이 남아 있습니다."
                    )
            ),
            List.of(
                    new HypothesisAiInput.StatementInput(
                            STATEMENT_ID,
                            "W-2026-0405-01",
                            StatementType.INITIAL,
                            LocalDateTime.of(2026, 10, 21, 10, 0),
                            "현장 앞에서 사람을 봤습니다."
                    )
            ),
            List.of()
    );

    // ==================== 정상 응답 테스트 ====================

    @ParameterizedTest(name = "가설 {0}개 허용")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("가설이 1~3개이고 필수값이 정상이면 통과한다")
    void acceptValidCount(int count) {

        List<HypothesisCandidateResponse> candidates =
                Collections.nCopies(
                        count,
                        candidate(
                                60,
                                List.of(EVIDENCE_ID),
                                List.of(STATEMENT_ID)
                        )
                );

        assertDoesNotThrow(
                () -> validator.validate(input, candidates)
        );
    }

    @ParameterizedTest(name = "신뢰도 {0} 허용")
    @ValueSource(ints = {0, 100})
    @DisplayName("신뢰도 경계값인 0과 100을 허용한다")
    void acceptConfidenceBoundary(int confidence) {

        HypothesisCandidateResponse candidate = candidate(
                confidence,
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID)
        );

        assertDoesNotThrow(
                () -> validator.validate(input, List.of(candidate))
        );
    }

    @Test
    @DisplayName("근거 목록이 비어 있고 추가 확인 사항이 없어도 통과한다")
    void acceptEmptyReferences() {

        // null 목록은 거부하지만 빈 목록은 허용
        // candidate()의 추가 확인 사항은 null로 설정되어 있음
        HypothesisCandidateResponse candidate =
                candidate(60, List.of(), List.of());

        assertDoesNotThrow(
                () -> validator.validate(input, List.of(candidate))
        );
    }

    // ==================== 잘못된 응답 테스트 ====================

    @Test
    @DisplayName("가설 목록이 null·빈 목록·4개 이상이면 거부한다")
    void rejectInvalidCount() {

        assertRejected(null);

        assertRejected(List.of());

        assertRejected(
                Collections.nCopies(
                        4,
                        candidate(
                                60,
                                List.of(EVIDENCE_ID),
                                List.of(STATEMENT_ID)
                        )
                )
        );
    }

    @Test
    @DisplayName("목록 안에 null 후보가 있으면 거부한다")
    void rejectNullCandidate() {

        // List.of(null)은 목록 생성 자체에서 오류가 나므로 사용하지 않음
        assertRejected(Collections.singletonList(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("가설 내용이 null·빈 문자열·공백이면 거부한다")
    void rejectMissingContent(String content) {

        HypothesisCandidateResponse candidate =
                new HypothesisCandidateResponse(
                        content,
                        "테스트 판단 근거",
                        null,
                        60,
                        List.of(EVIDENCE_ID),
                        List.of(STATEMENT_ID)
                );

        assertRejected(List.of(candidate));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("판단 근거가 null·빈 문자열·공백이면 거부한다")
    void rejectMissingReason(String reason) {

        HypothesisCandidateResponse candidate =
                new HypothesisCandidateResponse(
                        "테스트 가설",
                        reason,
                        null,
                        60,
                        List.of(EVIDENCE_ID),
                        List.of(STATEMENT_ID)
                );

        assertRejected(List.of(candidate));
    }

    @ParameterizedTest(name = "잘못된 신뢰도: {0}")
    @NullSource
    @ValueSource(ints = {-1, 101})
    @DisplayName("신뢰도가 null이거나 0~100 범위를 벗어나면 거부한다")
    void rejectInvalidConfidence(Integer confidence) {

        assertRejected(List.of(
                candidate(
                        confidence,
                        List.of(EVIDENCE_ID),
                        List.of(STATEMENT_ID)
                )
        ));
    }

    @Test
    @DisplayName("증거물 또는 진술 근거 목록이 null이면 거부한다")
    void rejectNullReferenceLists() {

        // 증거물 근거 목록 누락
        assertRejected(List.of(
                candidate(60, null, List.of(STATEMENT_ID))
        ));

        // 진술 근거 목록 누락
        assertRejected(List.of(
                candidate(60, List.of(EVIDENCE_ID), null)
        ));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "EV-2026-0406-01"})
    @DisplayName("증거물 번호가 잘못됐거나 입력 자료에 없으면 거부한다")
    void rejectInvalidEvidenceId(String evidenceId) {

        assertRejected(List.of(
                candidate(
                        60,
                        Collections.singletonList(evidenceId),
                        List.of(STATEMENT_ID)
                )
        ));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {999})
    @DisplayName("진술 번호가 null이거나 입력 자료에 없으면 거부한다")
    void rejectInvalidStatementId(Integer statementId) {

        assertRejected(List.of(
                candidate(
                        60,
                        List.of(EVIDENCE_ID),
                        Collections.singletonList(statementId)
                )
        ));
    }

    // ==================== 테스트 준비·확인용 메서드 ====================

    // 검사할 신뢰도·근거 목록을 가진 후보 생성
    private HypothesisCandidateResponse candidate(
            Integer confidence,
            List<String> evidenceIds,
            List<Integer> statementIds
    ) {
        return new HypothesisCandidateResponse(
                "테스트 가설",
                "테스트 판단 근거",
                null,
                confidence,
                evidenceIds,
                statementIds
        );
    }

    // 검증 실패 시 지정한 502 예외가 발생하는지 확인
    private void assertRejected(
            List<HypothesisCandidateResponse> candidates
    ) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(input, candidates)
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                exception.getStatusCode()
        );
    }
}