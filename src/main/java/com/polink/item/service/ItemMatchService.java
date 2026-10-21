package com.polink.item.service;

import com.polink.item.dto.ItemMatchListResponse;
import com.polink.item.dto.ItemMatchResponse;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.repository.ItemRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/*
 * 분실물과 습득물의 후보 매칭을 처리하는 Service다.
 * 현재는 외부 AI 모델을 호출하지 않고,
 * 카테고리·물품명·장소의 유사도를 점수로 계산하는 1차 매칭 방식이다.
 * 실제 소유권을 확정하지 않으며, 시민이 반환 신청 전에 참고할 후보만 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemMatchService {

    // 매칭 후보를 조회하고 분실물 정보를 찾는 Repository다.
    private final ItemRepository itemRepository;

    // 화면에 너무 많은 후보가 표시되지 않도록 최대 10건만 반환한다.
    private static final int MAX_MATCH_COUNT = 10;

    /*
     * 로그인한 시민의 분실물을 기준으로 습득물 후보를 조회한다.
     * @param currentUserId JWT subject에서 꺼낸 로그인 시민의 회원 번호
     * @param lostItemId 매칭 기준이 되는 분실물 번호
     * @return 유사도 점수 내림차순으로 정렬된 습득물 후보 목록
     */
    public ItemMatchListResponse findMatches(
            Integer currentUserId,
            String lostItemId
    ) {
        // Service도 방어적으로 로그인 사용자 정보를 한 번 더 확인한다.
        if (currentUserId == null || currentUserId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 매칭 기준 분실물이 실제로 존재하는지 확인한다.
        Item lostItem = itemRepository.findById(lostItemId)
                .orElseThrow(() -> new ItemNotFoundException(lostItemId));

        // 습득물 번호(F-XXXX)를 보내는 실수를 막는다.
        if (lostItem.getItemType() != ItemType.LOST) {
            throw new IllegalArgumentException(
                    "분실물(LOST)만 매칭 기준으로 사용할 수 있습니다."
            );
        }

        // 다른 시민이 등록한 분실물로 후보를 조회하지 못하도록 차단한다.
        if (!lostItem.getRegistrantId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 등록한 분실물만 매칭할 수 있습니다."
            );
        }

        // 반환 절차가 시작됐거나 완료된 분실물은 다시 매칭하지 않는다.
        if (lostItem.getStatus() != ItemStatus.LOST) {
            throw new IllegalArgumentException(
                    "분실 중(LOST) 상태의 물품만 매칭할 수 있습니다."
            );
        }

        // 아직 보관 중인 습득물만 매칭 후보로 사용한다.
        List<ItemMatchResponse> matches = itemRepository
                .findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                        ItemType.FOUND,
                        ItemStatus.STORED
                )
                .stream()
                // 각 습득물의 카테고리·이름·장소 유사도를 계산한다.
                .map(foundItem -> createScoredMatch(lostItem, foundItem))
                // 유사점이 전혀 없는 물품은 후보 목록에서 제외한다.
                .filter(scoredMatch -> scoredMatch != null)
                // 점수가 높은 후보를 먼저 반환한다.
                .sorted(
                        Comparator.comparingInt(ScoredMatch::score)
                                .reversed()
                                // 같은 점수면 물품 번호순으로 결과를 고정한다.
                                .thenComparing(
                                        scoredMatch ->
                                                scoredMatch.response().foundItemId()
                                )
                )
                // 반환 후보 개수를 최대 10개로 제한한다.
                .limit(MAX_MATCH_COUNT)
                // 내부 점수 정보는 응답에 노출하지 않고 DTO만 반환한다.
                .map(ScoredMatch::response)
                .toList();

        return new ItemMatchListResponse(matches);
    }

    /*
     * 분실물과 습득물 한 쌍의 유사도 점수와 안내 문구를 만든다.
     * 카테고리 일치: 50점
     * 물품명 유사: 30점
     * 장소 유사: 20점
     */
    private ScoredMatch createScoredMatch(Item lostItem, Item foundItem) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 같은 카테고리는 가장 중요한 1차 매칭 기준이다.
        if (lostItem.getCategory().equals(foundItem.getCategory())) {
            score += 50;
            reasons.add("카테고리가 일치합니다.");
        }

        // 띄어쓰기·대소문자 차이를 제거한 뒤 물품명을 비교한다.
        if (hasTextOverlap(lostItem.getItemName(), foundItem.getItemName())) {
            score += 30;
            reasons.add("물품명이 유사합니다.");
        }

        // 같은 장소 또는 포함 관계의 장소명도 후보 근거로 사용한다.
        if (hasTextOverlap(lostItem.getLocation(), foundItem.getLocation())) {
            score += 20;
            reasons.add("장소 정보가 유사합니다.");
        }

        // 어떤 기준도 일치하지 않으면 매칭 후보로 반환하지 않는다.
        if (score == 0) {
            return null;
        }

        ItemMatchResponse response = new ItemMatchResponse(
                foundItem.getItemId(),
                String.join(" ", reasons)
        );

        return new ScoredMatch(response, score);
    }

    /*
     * 두 문자열을 공백·대소문자 차이 없이 비교한다.
     * 한 문자열이 다른 문자열을 포함하면 기본적인 유사성이 있다고 판단한다.
     */
    private boolean hasTextOverlap(String first, String second) {
        String normalizedFirst = normalizeText(first);
        String normalizedSecond = normalizeText(second);

        // 한 글자 우연 일치를 막기 위해 각각 두 글자 이상일 때만 비교한다.
        if (normalizedFirst.length() < 2 || normalizedSecond.length() < 2) {
            return false;
        }

        return normalizedFirst.contains(normalizedSecond)
                || normalizedSecond.contains(normalizedFirst);
    }

    // 비교 전에 공백을 없애고 소문자로 통일한다.
    private String normalizeText(String value) {
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    // 정렬에만 사용하는 내부 점수 객체다. API 응답에는 점수를 노출하지 않는다.
    private record ScoredMatch(
            ItemMatchResponse response,
            int score
    ) {
    }
}