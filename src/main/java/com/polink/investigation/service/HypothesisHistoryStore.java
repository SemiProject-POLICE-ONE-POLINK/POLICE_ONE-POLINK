package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 재분석 시 중복을 피하도록 이전 가설 내용을 서버 메모리에 보관
@Component
public class HypothesisHistoryStore {

    // AI에 전달할 이력이 너무 길어지지 않도록 최근 30개만 보관
    private static final int MAX_HISTORY_SIZE = 30;

    // 사건·담당 경찰별로 생성 이력을 구분
    private final Map<HistoryKey, List<String>> histories =
            new ConcurrentHashMap<>();

    // 해당 사건에서 이전에 생성한 가설 내용 조회
    public List<String> getHistory(
            String caseId,
            Integer officerId
    ) {
        HistoryKey key = new HistoryKey(caseId, officerId);

        return histories.getOrDefault(key, List.of());
    }

    // 정상 생성된 가설을 채택·기각 여부와 관계없이 이력에 추가
    public void remember(
            String caseId,
            Integer officerId,
            List<HypothesisAiCandidate> candidates
    ) {
        HistoryKey key = new HistoryKey(caseId, officerId);

        // 같은 사건·경찰의 이력 변경을 한 번에 처리
        histories.compute(key, (historyKey, previous) -> {

            List<String> updated = previous == null
                    ? new ArrayList<>()
                    : new ArrayList<>(previous);

            for (HypothesisAiCandidate candidate : candidates) {

                if (candidate == null
                        || candidate.content() == null
                        || candidate.content().isBlank()) {
                    continue;
                }

                String content = candidate.content().strip();

                // 같은 문장은 중복 보관하지 않고 최근 위치로 이동
                updated.remove(content);
                updated.add(content);
            }

            // 오래된 내용부터 제거해 최근 30개 유지
            while (updated.size() > MAX_HISTORY_SIZE) {
                updated.remove(0);
            }

            // 외부에서 보관 중인 목록을 직접 변경하지 못하도록 반환
            return List.copyOf(updated);
        });
    }

    // 사건 번호와 경찰 회원 번호를 묶는 메모리 조회용 키
    private record HistoryKey(
            String caseId,
            Integer officerId
    ) {
    }
}