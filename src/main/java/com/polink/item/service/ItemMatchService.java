package com.polink.item.service;

import com.polink.item.dto.ItemMatchListResponse;
import com.polink.item.dto.ItemMatchResponse;
import com.polink.item.dto.ai.ItemMatchAiInput;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.repository.ItemRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/*
 * 분실물과 습득물의 후보 매칭을 처리하는 Service다.
 * 실제 후보 물품 조회와 권한 검증은 이 Service가 담당한다.
 * 후보의 유사도 분석과 매칭 사유 생성은 ItemMatchAiClient 구현체에 맡긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemMatchService {

    // 매칭 기준 분실물과 습득물 후보를 조회하는 Repository다.
    private final ItemRepository itemRepository;

    // 현재 프로필에 맞는 규칙 기반 또는 OpenAI 기반 매칭 구현체다.
    private final ItemMatchAiClient itemMatchAiClient;

    // 화면에 너무 많은 후보가 표시되지 않도록 최대 10건만 반환한다.
    private static final int MAX_MATCH_COUNT = 10;

    /*
     * 유사도가 낮은 후보를 응답에서 제외하는 최소 점수다.
     * 카테고리 일치 점수인 50점 이상인 후보만 사용자에게 보여준다.
     */
    private static final int MIN_MATCH_SCORE = 50;

    /*
     * 로그인한 시민의 분실물을 기준으로 습득물 후보를 조회한다.
     * @param currentUserId JWT subject에서 꺼낸 로그인 시민의 회원 번호
     * @param lostItemId 매칭 기준이 되는 분실물 번호
     * @return 매칭 근거가 포함된 습득물 후보 목록
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
        List<Item> foundItems = itemRepository
                .findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
                        ItemType.FOUND,
                        ItemStatus.STORED
                );

        // Entity 전체를 외부 AI에 전달하지 않도록 최소 정보 DTO로 변환한다.
        ItemMatchAiInput aiInput = new ItemMatchAiInput(
                toAiItemInput(lostItem),
                foundItems.stream()
                        .map(this::toAiItemInput)
                        .toList()
        );

        // AI가 반환할 수 있는 후보 번호를 서버가 조회한 습득물 번호로 제한한다.
        // AI가 존재하지 않는 번호를 만들어 내도 응답에 노출하지 않는다.
        Set<String> allowedFoundItemIds = foundItems.stream()
                .map(Item::getItemId)
                .collect(Collectors.toSet());

        /*
         * AI 후보 번호로 실제 습득물 상세 정보를 빠르게 찾기 위한 Map이다.
         * 앞선 allowedFoundItemIds 검증으로 존재하지 않는 번호는 이미 제외된다.
         */
        Map<String, Item> foundItemsById = foundItems.stream()
                .collect(Collectors.toMap(
                        Item::getItemId,
                        foundItem -> foundItem
                ));

        // 같은 습득물 번호가 여러 번 반환되는 것을 막기 위한 집합이다.
        Set<String> returnedFoundItemIds = new HashSet<>();

        // 현재 프로필에 맞는 구현체가 후보와 매칭 사유를 생성한다.
        // local 환경에서는 RuleBasedItemMatchAiClient가 동작한다.
        List<ItemMatchResponse> matches = itemMatchAiClient.generateMatches(aiInput)
                .stream()
                // 비어 있거나 잘못된 AI 후보 응답은 기본적으로 제외한다.
                .filter(candidate ->
                        candidate != null
                                && candidate.foundItemId() != null
                                && !candidate.foundItemId().isBlank()
                                && candidate.reason() != null
                                && !candidate.reason().isBlank()

                                // 최소 기준 미달 또는 범위를 벗어난 AI 점수는 제외한다.
                                && candidate.score() >= MIN_MATCH_SCORE
                                && candidate.score() <= 100

                                // 서버가 조회한 실제 습득물 후보만 반환하도록 검증한다.
                                && allowedFoundItemIds.contains(
                                candidate.foundItemId()
                        )

                                // 같은 후보 번호는 응답에 한 번만 포함한다.
                                && returnedFoundItemIds.add(
                                candidate.foundItemId()
                        )
                )
                .map(candidate -> {
                    /*
                     * 앞에서 실제 DB 후보만 통과시켰으므로,
                     * 후보 번호로 습득물 상세 정보를 안전하게 조회할 수 있다.
                     */
                    Item foundItem = foundItemsById.get(candidate.foundItemId());

                    return new ItemMatchResponse(
                            foundItem.getItemId(),
                            foundItem.getItemName(),
                            foundItem.getCategory(),
                            foundItem.getDescription(),
                            foundItem.getLocation(),
                            foundItem.getOccurredAt().toLocalDate(),
                            foundItem.getStatus(),
                            foundItem.getImagePath(),

                            // AI가 만든 매칭 근거와 검증된 점수를 함께 반환한다.
                            candidate.reason(),
                            candidate.score()
                    );
                })
                // 화면에 너무 많은 후보가 표시되지 않도록 최대 10건만 반환한다.
                .limit(MAX_MATCH_COUNT)
                .toList();

        return new ItemMatchListResponse(matches);
    }

    /*
     * Item Entity를 AI 매칭에 필요한 최소 정보 DTO로 변환한다.
     * 등록자 번호 등 매칭과 무관한 정보는 외부 AI에 보내지 않는다.
     */
    private ItemMatchAiInput.ItemInput toAiItemInput(Item item) {
        return new ItemMatchAiInput.ItemInput(
                item.getItemId(),
                item.getItemType().name(),
                item.getItemName(),
                item.getCategory(),
                item.getDescription(),
                item.getLocation(),
                item.getOccurredAt()
        );
    }
}