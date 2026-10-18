package com.polink.item.service;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분실물·습득물의 등록 규칙을 담당한다. */
@Service
@RequiredArgsConstructor
// 조회 메서드는 실수로 DB를 수정하지 않도록 기본값을 readOnly로 둔다.
@Transactional(readOnly = true)
public class ItemService {

    // @RequiredArgsConstructor가 이 final 필드를 받는 생성자를 자동으로 만들어 주입한다.
    private final ItemRepository itemRepository;

    /**
     * Controller에서 전달한 유형에 맞는 등록 메서드를 선택한다.
     * switch를 사용하면 LOST와 FOUND 이외의 유형은 등록할 수 없다.
     */
    @Transactional
    public ItemCreateResponse registerItem(ItemCreateRequest request) {
        return switch (request.itemType()) {
            case LOST -> registerLostItem(request);
            case FOUND -> registerFoundItem(request);
        };
    }

    /** 분실물은 LOST 유형 및 LOST 상태로 등록한다. */
    @Transactional
    public ItemCreateResponse registerLostItem(ItemCreateRequest request) {
        // 분실물 주소로 들어오면 요청값과 상관없이 LOST로 고정한다.
        return register(request, ItemType.LOST, ItemStatus.LOST);
    }

    /** 습득물은 FOUND 유형 및 STORED 상태로 등록한다. */
    @Transactional
    public ItemCreateResponse registerFoundItem(ItemCreateRequest request) {
        // 습득물 주소로 들어오면 FOUND 유형, STORED(보관 중) 상태로 시작한다.
        return register(request, ItemType.FOUND, ItemStatus.STORED);
    }

    private ItemCreateResponse register(
            ItemCreateRequest request,
            ItemType itemType,
            ItemStatus initialStatus
    ) {
        // DTO는 요청 데이터를 담는 상자이고, Item은 DB에 저장할 객체다.
        Item item = new Item(
                createItemId(itemType),
                request.registrantId(),
                itemType,
                request.itemName(),
                request.category(),
                request.description(),
                request.location(),
                request.occurredAt(),
                initialStatus
        );

        // save()가 INSERT를 실행하고, 저장된 Item을 반환한다.
        return ItemCreateResponse.from(itemRepository.save(item));
    }

    /**
     * 물품 유형별 번호 규칙
     * - 분실물(LOST): L-0001, L-0002, ...
     * - 습득물(FOUND): F-0001, F-0002, ...
     *
     * 두 유형의 번호는 서로 영향을 주지 않고 각각 증가한다.
     * 예를 들어 분실물이 L-0115까지 있어도, 첫 습득물은 F-0001이 될 수 있다.
     */
    private String createItemId(ItemType itemType) {
        String prefix = itemType == ItemType.LOST ? "L-" : "F-";

        // DB에서 같은 유형의 마지막 물품 번호를 찾아 다음 순번을 계산한다.
        int nextNumber = itemRepository.findTopByItemTypeOrderByItemIdDesc(itemType)
                .map(Item::getItemId)
                .map(lastItemId -> extractNumber(lastItemId, prefix) + 1)
                .orElse(1);

        // 네 자리 번호 규칙을 지키기 위해 9999번까지만 허용한다.
        if (nextNumber > 9999) {
            throw new IllegalStateException("물품 번호를 더 이상 생성할 수 없습니다.");
        }

        // %04d는 1을 0001, 15를 0015처럼 네 자리 숫자로 바꾼다.
        return prefix + String.format("%04d", nextNumber);
    }

    /** 마지막 번호(L-0115 또는 F-0215)에서 숫자 부분만 꺼낸다. */
    private int extractNumber(String itemId, String prefix) {
        if (!itemId.startsWith(prefix)) {
            throw new IllegalStateException("물품 번호 형식이 올바르지 않습니다: " + itemId);
        }

        try {
            return Integer.parseInt(itemId.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("물품 번호 형식이 올바르지 않습니다: " + itemId, exception);
        }
    }
}
