package com.polink.item.service;

import com.polink.item.dto.ai.ItemMatchAiCandidate;
import com.polink.item.dto.ai.ItemMatchAiInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/*
 * 실제 OpenAI API를 사용하지 않는 기본 매칭 구현체다.
 *
 * ai-live 프로필이 아닐 때만 등록된다.
 * 따라서 API 키가 없거나 외부 AI를 쓰지 않는 로컬 환경에서도
 * 기존 규칙 기반 매칭 기능은 계속 정상 동작한다.
 */
@Component
@Profile("!item-ai-live")
public class RuleBasedItemMatchAiClient implements ItemMatchAiClient {

    /*
     * 분실물과 습득물 후보를 비교해 점수 높은 순서로 반환한다.
     * 점수는 API 응답에 공개하지 않고 정렬에만 사용한다.
     */
    @Override
    public List<ItemMatchAiCandidate> generateMatches(ItemMatchAiInput input) {
        return input.foundCandidates()
                .stream()
                // 후보 한 건마다 규칙 기반 유사도와 안내 문구를 만든다.
                .map(foundItem -> createScoredCandidate(
                        input.lostItem(),
                        foundItem
                ))
                // 유사한 정보가 하나도 없는 후보는 제외한다.
                .filter(Objects::nonNull)
                // 점수 높은 후보를 먼저, 같은 점수면 물품 번호순으로 정렬한다.
                .sorted(
                        Comparator.comparingInt(ScoredCandidate::score)
                                .reversed()
                                .thenComparing(
                                        scoredCandidate ->
                                                scoredCandidate.candidate().foundItemId()
                                )
                )
                // 내부 점수는 버리고 API에 필요한 후보 정보만 반환한다.
                .map(ScoredCandidate::candidate)
                .toList();
    }

    /*
     * 카테고리·물품명·장소 정보를 비교해 후보 한 건을 만든다.
     * 카테고리 일치: 50점, 물품명 유사: 30점, 장소 유사: 20점이다.
     */
    private ScoredCandidate createScoredCandidate(
            ItemMatchAiInput.ItemInput lostItem,
            ItemMatchAiInput.ItemInput foundItem
    ) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 같은 카테고리는 가장 중요한 기본 매칭 기준이다.
        if (lostItem.category().equals(foundItem.category())) {
            score += 50;
            reasons.add("카테고리가 일치합니다.");
        }

        // 물품명의 공백·대소문자 차이를 제거하고 포함 관계를 비교한다.
        if (hasTextOverlap(lostItem.itemName(), foundItem.itemName())) {
            score += 30;
            reasons.add("물품명이 유사합니다.");
        }

        // 장소명도 같은 장소 또는 포함 관계라면 유사하다고 판단한다.
        if (hasTextOverlap(lostItem.location(), foundItem.location())) {
            score += 20;
            reasons.add("장소 정보가 유사합니다.");
        }

        // 일치하는 정보가 없으면 매칭 후보를 만들지 않는다.
        if (score == 0) {
            return null;
        }

        return new ScoredCandidate(
                new ItemMatchAiCandidate(
                        foundItem.itemId(),
                        String.join(" ", reasons)
                ),
                score
        );
    }

    /*
     * 두 문자열을 공백·대소문자 차이 없이 비교한다.
     * 한 문자열이 다른 문자열을 포함하면 기본적인 유사성이 있다고 판단한다.
     */
    private boolean hasTextOverlap(String first, String second) {
        String normalizedFirst = normalizeText(first);
        String normalizedSecond = normalizeText(second);

        // 한 글자 우연 일치를 막기 위해 두 글자 이상일 때만 비교한다.
        if (normalizedFirst.length() < 2 || normalizedSecond.length() < 2) {
            return false;
        }

        return normalizedFirst.contains(normalizedSecond)
                || normalizedSecond.contains(normalizedFirst);
    }

    // 비교 전에 공백을 없애고, 언어 영향 없이 소문자로 통일한다.
    private String normalizeText(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /*
     * 규칙 기반 정렬에만 사용하는 내부 객체다.
     * score는 외부 API 응답에 노출하지 않는다.
     */
    private record ScoredCandidate(
            ItemMatchAiCandidate candidate,
            int score
    ) {
    }
}