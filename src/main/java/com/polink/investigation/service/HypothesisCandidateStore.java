package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 생성된 가설 후보를 채택 전까지 서버 메모리에 임시 보관
@Component
public class HypothesisCandidateStore {

    // 가설 후보를 생성한 시점부터 15분까지만 채택할 수 있도록 하는 유효 기간
    private static final Duration EXPIRATION =
            Duration.ofMinutes(15);

    // 여러 요청이 동시에 접근해도 사용할 수 있는 메모리 저장소
    private final Map<String, StoredCandidate> candidates =
            new ConcurrentHashMap<>();

    // 현재 가설 채택을 처리하고 있는 분석 번호
    private final Set<String> adoptingAnalyses =
            new HashSet<>();

    // 후보를 보관하고, 채택 요청에 사용할 임시 번호를 반환
    // 기존 호출 코드와 테스트에서 사용하던 저장 방식 유지
    public String put(
            String caseId,
            Integer officerId,
            HypothesisAiCandidate candidate,
            int confidence
    ) {
        return put(
                UUID.randomUUID().toString(),
                caseId,
                officerId,
                candidate,
                confidence
        );
    }

    // 같은 분석에서 생성된 후보들을 동일한 분석 번호로 보관
    public synchronized String put(
            String analysisId,
            String caseId,
            Integer officerId,
            HypothesisAiCandidate candidate,
            int confidence
    ) {
        Instant now = Instant.now();

        // 이미 보관 기간이 지난 후보 정리
        cleanupExpired(now);

        String candidateId = UUID.randomUUID().toString();

        candidates.put(
                candidateId,
                new StoredCandidate(
                        analysisId,
                        caseId,
                        officerId,
                        candidate,
                        confidence,
                        now.plus(EXPIRATION)
                )
        );

        return candidateId;
    }

    // 번호에 해당하는 후보가 없거나 만료되었으면 빈 결과 반환
    public synchronized Optional<StoredCandidate> find(
            String candidateId
    ) {
        StoredCandidate stored = candidates.get(candidateId);

        if (stored == null) {
            return Optional.empty();
        }

        if (!Instant.now().isBefore(stored.expiresAt())) {
            candidates.remove(candidateId);
            return Optional.empty();
        }

        return Optional.of(stored);
    }

    // 같은 분석에서 하나의 채택 요청만 처리하도록 시작 상태 설정
    public synchronized boolean tryBeginAdoption(
            String candidateId
    ) {
        Optional<StoredCandidate> stored = find(candidateId);

        if (stored.isEmpty()) {
            return false;
        }

        // 이미 채택 처리 중인 분석이면 false 반환
        return adoptingAnalyses.add(
                stored.get().analysisId()
        );
    }

    // DB 저장 성공 후 같은 분석의 모든 후보를 채택 대상에서 제외
    public synchronized void completeAdoption(
            String analysisId
    ) {
        candidates.entrySet().removeIf(
                entry -> analysisId.equals(
                        entry.getValue().analysisId()
                )
        );

        adoptingAnalyses.remove(analysisId);
    }

    // DB 변경이 취소되면 해당 분석의 채택 처리 상태 해제
    public synchronized void cancelAdoption(
            String analysisId
    ) {
        adoptingAnalyses.remove(analysisId);
    }

    // 채택 후 임시 후보 제거
    public synchronized void remove(String candidateId) {
        candidates.remove(candidateId);
    }

    // 이미 보관 기간이 지난 후보 정리
    private void cleanupExpired(Instant now) {
        candidates.entrySet().removeIf(
                entry -> !now.isBefore(
                        entry.getValue().expiresAt()
                )
        );

        // 남아 있는 후보들의 분석 번호 수집
        Set<String> activeAnalysisIds = new HashSet<>();

        for (StoredCandidate stored : candidates.values()) {
            activeAnalysisIds.add(stored.analysisId());
        }

        // 남아 있는 후보가 없는 분석의 채택 처리 상태 제거
        adoptingAnalyses.retainAll(activeAnalysisIds);
    }

    // 가설 내용과 함께 사건·담당 경찰·서버 계산 점수를 보관
    // 같은 분석에서 생성된 후보들은 analysisId를 공유
    public record StoredCandidate(
            String analysisId,
            String caseId,
            Integer officerId,
            HypothesisAiCandidate candidate,
            int confidence,
            Instant expiresAt
    ) {
    }
}