package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import com.polink.investigation.entity.StatementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// DB나 실제 AI 호출 없이 AI 원본 후보의 검증 규칙만 테스트
class HypothesisResponseValidatorTest {

    private static final String EVIDENCE_ID = "EV-2026-0405-01";
    private static final String OTHER_EVIDENCE_ID = "EV-2026-0405-02";
    private static final Integer STATEMENT_ID = 7;
    private static final Integer OTHER_STATEMENT_ID = 8;

    private final HypothesisResponseValidator validator =
            new HypothesisResponseValidator();

    // AI에게 전달했다고 가정할 사건 자료
    private final HypothesisAiInput input = new HypothesisAiInput(
            "SMPA-2026-0405",
            "테스트 사건",
            "가상 사건 내용입니다.",
            List.of(
                    new HypothesisAiInput.EvidenceInput(
                            EVIDENCE_ID,
                            "현장 사진",
                            "출입구에 신발 자국이 남아 있습니다."
                    ),
                    new HypothesisAiInput.EvidenceInput(
                            OTHER_EVIDENCE_ID,
                            "CCTV 영상",
                            "현장 주변 차량이 찍혀 있습니다."
                    )
            ),
            List.of(
                    new HypothesisAiInput.StatementInput(
                            STATEMENT_ID,
                            "W-2026-0405-01",
                            StatementType.INITIAL,
                            LocalDateTime.of(2026, 10, 21, 10, 0),
                            "현장 앞에서 사람을 봤습니다."
                    ),
                    new HypothesisAiInput.StatementInput(
                            OTHER_STATEMENT_ID,
                            "W-2026-0405-02",
                            StatementType.INITIAL,
                            LocalDateTime.of(2026, 10, 21, 10, 5),
                            "다른 차량을 봤습니다."
                    )
            ),
            List.of()
    );

    @ParameterizedTest(name = "가설 {0}개 허용")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("정상 후보는 1~3개까지 허용한다")
    void acceptValidCandidateCount(int count) {
        assertDoesNotThrow(() -> validator.validate(
                input,
                Collections.nCopies(count, validCandidate())
        ));
    }

    @Test
    @DisplayName("근거가 없어도 빈 목록으로 전달하면 허용한다")
    void acceptEmptyReferenceLists() {
        HypothesisAiCandidate candidate = candidate(
                "추가 확인이 필요한 가설",
                "직접 뒷받침하는 자료 없음",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertDoesNotThrow(
                () -> validator.validate(input, List.of(candidate))
        );
    }

    @Test
    @DisplayName("실제 사건 자료를 사용한 반대 근거와 교차 확인을 허용한다")
    void acceptOppositionAndCorroboration() {
        HypothesisAiCandidate candidate = candidate(
                "테스트 가설",
                "증거물과 진술을 함께 검토했습니다.",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(OTHER_EVIDENCE_ID),
                List.of(OTHER_STATEMENT_ID),
                List.of(new HypothesisAiCandidate.Corroboration(
                        EVIDENCE_ID,
                        STATEMENT_ID,
                        "현장 주변에 사람이 있었다"
                )),
                List.of("목격 시각에 차이가 있습니다.")
        );

        assertDoesNotThrow(
                () -> validator.validate(input, List.of(candidate))
        );
    }

    @Test
    @DisplayName("후보가 없거나 4개 이상이면 거부한다")
    void rejectInvalidCandidateCount() {
        assertRejected((List<HypothesisAiCandidate>) null);
        assertRejected(List.of());
        assertRejected(Collections.nCopies(4, validCandidate()));
    }

    @Test
    @DisplayName("목록 안에 null 후보가 있으면 거부한다")
    void rejectNullCandidate() {
        // List.of(null)은 목록 생성 단계에서 실패하므로 사용하지 않는다.
        assertRejected(Collections.singletonList(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("가설 내용이 없으면 거부한다")
    void rejectMissingContent(String content) {
        assertRejected(candidate(
                content,
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("판단 근거가 없으면 거부한다")
    void rejectMissingReason(String reason) {
        assertRejected(candidate(
                "테스트 가설",
                reason,
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @ParameterizedTest(name = "누락된 목록 위치: {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    @DisplayName("필수 목록이 null이면 거부한다")
    void rejectNullReferenceList(int missingIndex) {
        HypothesisAiCandidate candidate = candidate(
                "테스트 가설",
                "판단 근거",
                missingIndex == 0 ? null : List.of(EVIDENCE_ID),
                missingIndex == 1 ? null : List.of(STATEMENT_ID),
                missingIndex == 2 ? null : List.of(),
                missingIndex == 3 ? null : List.of(),
                missingIndex == 4 ? null : List.of(),
                missingIndex == 5 ? null : List.of()
        );

        assertRejected(candidate);
    }

    @Test
    @DisplayName("입력 자료에 없는 지지 근거 번호를 거부한다")
    void rejectUnknownSupportingIds() {
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of("EV-NOT-FOUND"),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(999),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @Test
    @DisplayName("입력 자료에 없는 반대 근거 번호를 거부한다")
    void rejectUnknownOpposingIds() {
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of("EV-NOT-FOUND"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(999),
                List.of(),
                List.of()
        ));
    }

    @Test
    @DisplayName("교차 확인이 지지 근거를 가리키지 않으면 거부한다")
    void rejectInvalidCorroboration() {
        // 실제 자료이지만 이 후보의 지지 증거물 목록에는 없다.
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(new HypothesisAiCandidate.Corroboration(
                        OTHER_EVIDENCE_ID,
                        STATEMENT_ID,
                        "같은 사실"
                )),
                List.of()
        ));

        // 공통으로 확인한 사실이 비어 있다.
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(new HypothesisAiCandidate.Corroboration(
                        EVIDENCE_ID,
                        STATEMENT_ID,
                        " "
                )),
                List.of()
        ));
    }

    @Test
    @DisplayName("같은 자료 번호가 한 목록에 반복되면 거부한다")
    void rejectDuplicateIds() {
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID, EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(OTHER_EVIDENCE_ID, OTHER_EVIDENCE_ID),
                List.of(),
                List.of(),
                List.of()
        ));
    }

    @Test
    @DisplayName("비어 있는 정황 문장을 거부한다")
    void rejectBlankCircumstance() {
        assertRejected(candidate(
                "테스트 가설",
                "판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of(" ")
        ));
    }

    // 각 테스트에서 기본으로 사용할 정상 AI 원본 후보
    private HypothesisAiCandidate validCandidate() {
        return candidate(
                "테스트 가설",
                "테스트 판단 근거",
                List.of(EVIDENCE_ID),
                List.of(STATEMENT_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    // 원하는 필드만 바꾼 AI 원본 후보를 테스트에서 쉽게 만들기 위한 메서드
    private HypothesisAiCandidate candidate(
            String content,
            String reason,
            List<String> evidenceIds,
            List<Integer> statementIds,
            List<String> opposingEvidenceIds,
            List<Integer> opposingStatementIds,
            List<HypothesisAiCandidate.Corroboration> corroborations,
            List<String> circumstances
    ) {
        return new HypothesisAiCandidate(
                content,
                reason,
                null, // 추가 확인 사항은 선택 입력
                evidenceIds,
                statementIds,
                List.of(),
                opposingEvidenceIds,
                opposingStatementIds,
                corroborations,
                circumstances
        );
    }

    private void assertRejected(HypothesisAiCandidate candidate) {
        assertRejected(List.of(candidate));
    }

    // 검증 실패 시 502 오류가 발생하는지 확인
    private void assertRejected(List<HypothesisAiCandidate> candidates) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(input, candidates)
        );

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    }
}