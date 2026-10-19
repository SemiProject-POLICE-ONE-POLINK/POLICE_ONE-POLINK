package com.polink.item.service;

import com.polink.item.dto.ItemCreateRequest;
import com.polink.item.dto.ItemCreateResponse;
import com.polink.item.dto.ItemDetailResponse;
import com.polink.item.dto.ItemListResponse;
import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import com.polink.item.exception.ItemNotFoundException;
import com.polink.item.repository.ItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.polink.item.dto.ItemPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** 분실물·습득물의 등록 규칙을 담당한다. */
@Service
@RequiredArgsConstructor
// 조회 메서드는 실수로 DB를 수정하지 않도록 기본값을 readOnly로 둔다.
@Transactional(readOnly = true)
public class ItemService {

    // @RequiredArgsConstructor가 이 final 필드를 받는 생성자를 자동으로 만들어 주입한다.
    private final ItemRepository itemRepository;
    private final ItemImageService itemImageService;

    /**
     * 물품 목록을 검색 조건과 페이지 번호에 맞춰 조회한다.
     * page는 0부터 시작한다.
     * size는 한 페이지에서 보여 줄 물품 개수다.
     */
    public ItemPageResponse getItems(
            ItemType itemType,
            String category,
            String keyword,
            ItemStatus status,
            int page,
            int size
    ) {
        // 음수 페이지는 존재하지 않으므로 요청 오류로 처리한다.
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        // 한 번에 너무 많은 데이터를 조회하지 않도록 범위를 제한한다.
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        // page와 size를 Spring Data JPA에 전달한다.
        Pageable pageable = PageRequest.of(page, size);

        // DB에서 필요한 페이지 데이터와 전체 개수를 함께 조회한다.
        Page<Item> itemPage = itemRepository.findItemsByConditions(
                itemType,
                normalize(category),
                normalize(keyword),
                status,
                pageable
        );

        // Entity를 API 응답용 DTO로 변환한다.
        List<ItemListResponse> items = itemPage.getContent()
                .stream()
                .map(ItemListResponse::from)
                .toList();

        // 목록과 페이지 정보를 함께 반환한다.
        return new ItemPageResponse(
                items,
                itemPage.getNumber(),
                itemPage.getSize(),
                itemPage.getTotalElements(),
                itemPage.getTotalPages(),
                itemPage.isFirst(),
                itemPage.isLast()
        );
    }

    /*
     * 로그인한 사용자가 등록한 물품 목록을 페이지 단위로 조회한다.
     * 현재는 Controller가 아직 로그인 userId를 전달하지 않는다.
     * 로그인 기능이 연결되면 인증정보의 userId를 registrantId 자리에 전달한다.
     */
    public ItemPageResponse getMyItems(
            Integer registrantId,
            int page,
            int size
    ) {
        // 로그인 사용자 ID가 없으면 정상적인 내 물품 조회를 할 수 없다.
        if (registrantId == null || registrantId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }

        // 페이지 번호는 0부터 시작한다.
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }

        // 한 번에 과도하게 많은 데이터를 가져오지 않도록 제한한다.
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        // 등록자 ID와 페이지 정보를 Repository에 전달한다.
        Pageable pageable = PageRequest.of(page, size);

        // 해당 사용자가 등록한 물품만 최신 등록순으로 조회한다.
        Page<Item> itemPage = itemRepository
                .findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                        registrantId,
                        pageable
                );

        // Entity 목록을 API 응답용 DTO 목록으로 바꾼다.
        List<ItemListResponse> items = itemPage.getContent()
                .stream()
                .map(ItemListResponse::from)
                .toList();

        // 목록과 페이지 정보를 함께 반환한다.
        return new ItemPageResponse(
                items,
                itemPage.getNumber(),
                itemPage.getSize(),
                itemPage.getTotalElements(),
                itemPage.getTotalPages(),
                itemPage.isFirst(),
                itemPage.isLast()
        );
    }

    /** 물품 번호로 한 건의 상세 정보를 조회한다. */
    public ItemDetailResponse getItemDetail(String itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        return ItemDetailResponse.from(item);
    }

    /**
     * Controller에서 전달한 유형에 맞는 등록 메서드를 선택한다.
     * switch를 사용하면 LOST와 FOUND 이외의 유형은 등록할 수 없다.
     */
    @Transactional
    public ItemCreateResponse registerItem(ItemCreateRequest request) {
        return registerItem(request, null);
    }

    /** 이미지가 포함된 multipart 요청에서 호출하는 등록 메서드다. */
    @Transactional
    public ItemCreateResponse registerItem(ItemCreateRequest request, MultipartFile image) {
        return switch (request.itemType()) {
            case LOST -> register(request, ItemType.LOST, ItemStatus.LOST, image);
            case FOUND -> register(request, ItemType.FOUND, ItemStatus.STORED, image);
        };
    }

    private ItemCreateResponse register(
            ItemCreateRequest request,
            ItemType itemType,
            ItemStatus initialStatus,
            MultipartFile image
    ) {
        String imagePath = itemImageService.save(image);
        registerImageCleanupAfterRollback(imagePath);

        // DTO는 요청 데이터를 담는 상자이고, Item은 DB에 저장할 객체다.
        Item item = new Item(
                createItemId(itemType),
                request.registrantId(),
                itemType,
                request.itemName(),
                request.category(),
                request.description(),
                request.location(),
                // 요청은 날짜만 받는다.
                // DB의 DATETIME 컬럼에는 해당 날짜의 자정(00:00:00)으로 저장한다.
                request.occurredAt().atStartOfDay(),
                initialStatus,
                imagePath
        );
        // save()가 INSERT를 실행하고, 저장된 Item을 반환한다.
        return ItemCreateResponse.from(itemRepository.save(item));
    }

    /**
     * 물품 유형별 번호 규칙
     * - 분실물(LOST): L-0001, L-0002, ...
     * - 습득물(FOUND): F-0001, F-0002, ...
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

    /** 공백만 보낸 검색 조건은 조건 없이 조회한다는 뜻의 null로 바꾼다. */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** DB 트랜잭션이 실패하면, DB에 연결되지 못한 이미지 파일을 삭제한다. */
    private void registerImageCleanupAfterRollback(String imagePath) {
        if (imagePath == null) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        itemImageService.delete(imagePath);
                    } catch (RuntimeException exception) {
                        log.error("DB 저장 롤백 후 물품 이미지 삭제 실패: {}", imagePath, exception);
                    }
                }
            }
        });
    }

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);
}
