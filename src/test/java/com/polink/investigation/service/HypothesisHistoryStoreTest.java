package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// 실제 객체를 사용해 이전 가설 이력의 보관·구분·최대 개수 검사
class HypothesisHistoryStoreTest {

    private static final String CASE_ID = "SMPA-2026-0405";
    private static final String OTHER_CASE_ID = "SMPA-2026-0406";
    private static final Integer OFFICER_ID = 7;
    private static final Integer OTHER_OFFICER_ID = 8;

    private HypothesisHistoryStore historyStore;

    @BeforeEach
    void setUp() {
        historyStore = new HypothesisHistoryStore();
    }

    @Test
    @DisplayName("생성 이력이 없으면 빈 목록을 반환한다")
    void returnEmptyHistory() {
        List<String> history =
                historyStore.getHistory(CASE_ID, OFFICER_ID);

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("다시 분석해 생성한 가설을 기존 이력에 추가한다")
    void accumulateGeneratedHypotheses() {
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate("가설 A"))
        );

        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate("가설 B"))
        );

        assertEquals(
                List.of("가설 A", "가설 B"),
                historyStore.getHistory(CASE_ID, OFFICER_ID)
        );
    }

    @Test
    @DisplayName("다른 사건이나 다른 경찰의 생성 이력이 섞이지 않는다")
    void separateHistoryByCaseAndOfficer() {
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate("7번 경찰의 첫 사건 가설"))
        );

        historyStore.remember(
                OTHER_CASE_ID,
                OFFICER_ID,
                List.of(candidate("7번 경찰의 다른 사건 가설"))
        );

        historyStore.remember(
                CASE_ID,
                OTHER_OFFICER_ID,
                List.of(candidate("8번 경찰의 가설"))
        );

        assertEquals(
                List.of("7번 경찰의 첫 사건 가설"),
                historyStore.getHistory(CASE_ID, OFFICER_ID)
        );

        assertEquals(
                List.of("7번 경찰의 다른 사건 가설"),
                historyStore.getHistory(OTHER_CASE_ID, OFFICER_ID)
        );

        assertEquals(
                List.of("8번 경찰의 가설"),
                historyStore.getHistory(CASE_ID, OTHER_OFFICER_ID)
        );

        assertTrue(
                historyStore.getHistory(
                        OTHER_CASE_ID,
                        OTHER_OFFICER_ID
                ).isEmpty()
        );
    }

    @Test
    @DisplayName("같은 가설을 다시 보관하면 중복 없이 최근 위치로 이동한다")
    void moveRepeatedContentToLatestPosition() {
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(
                        candidate("가설 A"),
                        candidate("가설 B")
                )
        );

        // 앞뒤 공백은 제거하고 기존 가설과 비교
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate("  가설 A  "))
        );

        assertEquals(
                List.of("가설 B", "가설 A"),
                historyStore.getHistory(CASE_ID, OFFICER_ID)
        );
    }

    @Test
    @DisplayName("가설 이력이 30개를 초과하면 오래된 내용부터 제거한다")
    void keepLatestThirtyHypotheses() {
        List<HypothesisAiCandidate> candidates =
                new ArrayList<>();

        for (int number = 1; number <= 31; number++) {
            candidates.add(candidate("가설 " + number));
        }

        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                candidates
        );

        List<String> history =
                historyStore.getHistory(CASE_ID, OFFICER_ID);

        assertEquals(30, history.size());
        assertFalse(history.contains("가설 1"));
        assertEquals("가설 2", history.get(0));
        assertEquals("가설 31", history.get(29));
    }

    @Test
    @DisplayName("내용이 없는 후보는 이력에 추가하지 않는다")
    void skipCandidatesWithoutContent() {
        // null 후보를 포함하기 위해 Arrays.asList 사용
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                Arrays.asList(
                        null,
                        candidate(null),
                        candidate(""),
                        candidate("   "),
                        candidate("정상 가설")
                )
        );

        assertEquals(
                List.of("정상 가설"),
                historyStore.getHistory(CASE_ID, OFFICER_ID)
        );
    }

    @Test
    @DisplayName("조회한 목록을 외부에서 변경해 저장된 이력을 훼손할 수 없다")
    void preventExternalHistoryModification() {
        historyStore.remember(
                CASE_ID,
                OFFICER_ID,
                List.of(candidate("가설 A"))
        );

        List<String> history =
                historyStore.getHistory(CASE_ID, OFFICER_ID);

        assertThrows(
                UnsupportedOperationException.class,
                () -> history.add("외부에서 추가한 가설")
        );

        assertEquals(
                List.of("가설 A"),
                historyStore.getHistory(CASE_ID, OFFICER_ID)
        );
    }

    // 이력 검사는 가설 내용만 사용하므로 나머지는 기본값 설정
    private HypothesisAiCandidate candidate(String content) {
        return new HypothesisAiCandidate(
                content,
                "테스트 판단 근거",
                "현장 자료 추가 확인",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}