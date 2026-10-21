package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 생성된 가설 후보를 채택 전까지 서버 메모리에 임시 보관
@Component
public class HypothesisCandidateStore {

    // 가설 후보를 생성한 시점부터 15분까지만 채택할 수 있도록 하는 유효 기간
    private static final Duration EXPIRATION = Duration.ofMinutes(15);

    // 여러 요청이 동시에 접근해도 사용할 수 있는 메모리 저장소
    private final Map<String, StoredCandidate> candidates = new ConcurrentHashMap<>();

    // 후보를 보관하고, 채택 요청에 사용할 임시 번호를 반환
    public String put(
            String caseId,
            Integer officerId,
            HypothesisAiCandidate candidate,
            int confidence
    ) {
        Instant now = Instant.now();

        // 이미 보관 기간이 지난 후보 정리
        candidates.entrySet().removeIf(
                entry -> !now.isBefore(entry.getValue().expiresAt())
        );

        String candidateId = UUID.randomUUID().toString();

        candidates.put(
                candidateId,
                new StoredCandidate(
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
    public Optional<StoredCandidate> find(String candidateId) {
        StoredCandidate stored = candidates.get(candidateId);

        if (stored == null) {
            return Optional.empty();
        }

        if (!Instant.now().isBefore(stored.expiresAt())) {
            candidates.remove(candidateId, stored);
            return Optional.empty();
        }

        return Optional.of(stored);
    }

    // 채택 후 임시 후보 제거
    public void remove(String candidateId) {
        candidates.remove(candidateId);
    }

    // 가설 내용과 함께 사건·담당 경찰·서버 계산 점수를 보관
    public record StoredCandidate(
            String caseId,
            Integer officerId,
            HypothesisAiCandidate candidate,
            int confidence,
            Instant expiresAt
    ) {
    }
}
