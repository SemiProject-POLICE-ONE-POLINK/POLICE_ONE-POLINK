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
import com.polink.item.dto.ItemUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.polink.item.dto.ItemStatisticsResponse;
import com.polink.item.dto.ItemCategoryCountResponse;
import com.polink.item.dto.ItemDailyCountResponse;
import com.polink.item.dto.ItemMonthlyCountResponse;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.EnumMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 분실물·습득물의 등록 규칙을 담당한다. */
@Service
@RequiredArgsConstructor

// 조회 메서드는 실수로 DB를 수정하지 않도록 기본값을 readOnly로 둔다.
@Transactional(readOnly = true)
public class ItemService {

    // @RequiredArgsConstructor가 이 final 필드를 받는 생성자를 자동으로 만들어 주입한다.
    private final ItemRepository itemRepository;
    private final ItemImageService itemImageService;

    /*
     * 물품 목록을 선택 검색 조건과 페이지 번호에 맞춰 조회한다.
     * 발생일은 startDate와 endDate 범위에 포함된 물품만 조회한다.
     */
    public ItemPageResponse getItems(
            ItemType itemType,
            String category,
            String keyword,
            String location,
            ItemStatus status,
            LocalDate startDate,
            LocalDate endDate,
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

        // 시작일이 종료일보다 늦으면 의미 없는 검색 범위다.
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }

        /*
         * Item.occurredAt은 LocalDateTime 타입이다.
         * startDate는 해당 날짜 00:00부터 포함한다.
         * endDate는 다음 날 00:00 미만으로 조회해, 선택한 종료일 하루 전체를 포함한다.
         */
        LocalDateTime startDateTime = startDate == null
                ? null
                : startDate.atStartOfDay();

        LocalDateTime endDateExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay();

        // page와 size를 Spring Data JPA에 전달한다.
        Pageable pageable = PageRequest.of(page, size);

        // DB에서 필터에 맞는 페이지 데이터와 전체 개수를 함께 조회한다.
        Page<Item> itemPage = itemRepository.findItemsByConditions(
                itemType,
                normalize(category),
                normalize(keyword),
                normalize(location),
                status,
                startDateTime,
                endDateExclusive,
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
     * itemType과 status는 선택 필터다.
     */
    public ItemPageResponse getMyItems(
            Integer registrantId,
            ItemType itemType,
            ItemStatus status,
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

        // 등록자 ID, 선택 필터, 페이지 정보를 Repository에 전달한다.
        // registrantId는 요청값이 아니라 JWT subject에서만 전달되므로 소유권은 유지된다.
        Pageable pageable = PageRequest.of(page, size);

        // 해당 사용자가 등록한 물품만 최신 등록순으로 조회한다.
        // 유형·상태가 null이면 Repository의 선택 조건이 비활성화되어 전체를 조회한다.
        Page<Item> itemPage = itemRepository
                .findMyItemsByConditions(
                        registrantId,
                        itemType,
                        status,
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

    /**
     * 기존 Service 호출부와 단위 테스트를 깨지 않기 위한 호환용 진입점이다.
     * 필터를 전달하지 않은 기존 요청은 이전과 완전히 동일하게 내 물품 전체를 조회한다.
     */
    public ItemPageResponse getMyItems(
            Integer registrantId,
            int page,
            int size
    ) {
        // 이전 호출은 기존 Repository 메서드를 그대로 사용한다.
        // 이렇게 하면 새 필터 API를 추가해도 기존 서비스 사용자와 테스트의 동작은 바뀌지 않는다.
        if (registrantId == null || registrantId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }

        Page<Item> itemPage = itemRepository
                .findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
                        registrantId,
                        PageRequest.of(page, size)
                );

        List<ItemListResponse> items = itemPage.getContent()
                .stream()
                .map(ItemListResponse::from)
                .toList();

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

    /**
     * 물품 번호로 한 건의 상세 정보를 조회한다.
     */
    public ItemDetailResponse getItemDetail(String itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        return ItemDetailResponse.from(item);
    }

    /*
     * 로그인한 사용자의 ID를 받아 물품을 등록한다.
     * registrantId는 요청 본문이 아니라 인증 정보에서 전달받는다.
     */
    @Transactional
    public ItemCreateResponse registerItem(
            ItemCreateRequest request,
            Integer registrantId,
            MultipartFile image
    ) {
        if (registrantId == null || registrantId < 1) {
            throw new IllegalArgumentException("로그인 사용자 정보가 필요합니다.");
        }

        return switch (request.itemType()) {
            case LOST -> register(
                    request,
                    registrantId,
                    ItemType.LOST,
                    ItemStatus.LOST,
                    image
            );
            case FOUND -> register(
                    request,
                    registrantId,
                    ItemType.FOUND,
                    ItemStatus.STORED,
                    image
            );
        };
    }

    private ItemCreateResponse register(
            ItemCreateRequest request,
            Integer registrantId,
            ItemType itemType,
            ItemStatus initialStatus,
            MultipartFile image
    ) {
        String imagePath = itemImageService.save(image);
        registerImageCleanupAfterRollback(imagePath);

        Item item = new Item(
                createItemId(itemType),
                registrantId,
                itemType,
                request.itemName(),
                request.category(),
                request.description(),
                request.location(),
                request.occurredAt().atStartOfDay(),
                initialStatus,
                imagePath
        );

        return ItemCreateResponse.from(itemRepository.save(item));
    }

    /*
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

    /**
     * 마지막 번호(L-0115 또는 F-0215)에서 숫자 부분만 꺼낸다.
     */
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

    /**
     * 공백만 보낸 검색 조건은 조건 없이 조회한다는 뜻의 null로 바꾼다.
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * DB 트랜잭션이 실패하면, DB에 연결되지 못한 이미지 파일을 삭제한다.
     */
    private void registerImageCleanupAfterRollback(String imagePath) {
        if (imagePath == null) {
            return;
        }

        // 단위 테스트처럼 Spring 트랜잭션 밖에서 직접 호출된 경우에는
        // 등록할 롤백 콜백이 없으므로 예외 없이 그대로 종료한다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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

    /*
     * 경찰이 등록된 습득물의 기본 정보를 수정한다.
     * 습득물은 보관 중(STORED) 상태일 때만 수정할 수 있다.
     * 인계 대기 또는 반환 완료된 습득물은 수정할 수 없다.
     */
    @Transactional
    public ItemDetailResponse updateFoundItem(
            String itemId,
            Integer currentPoliceId,
            ItemUpdateRequest request
    ) {
        // 경찰 사용자 정보가 없으면 수정 요청을 처리할 수 없다.
        if (currentPoliceId == null || currentPoliceId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        // 수정 대상 물품이 없으면 기존 상세 조회와 동일하게 404를 반환한다.
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        // 경찰 수정 API에서는 습득물만 수정할 수 있다.
        if (item.getItemType() != ItemType.FOUND) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "습득물만 수정할 수 있습니다."
            );
        }

        // 반환 절차가 시작됐거나 완료된 습득물은 정보 변경을 막는다.
        if (item.getStatus() != ItemStatus.STORED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "보관 중 상태의 습득물만 수정할 수 있습니다."
            );
        }

        // 유형, 등록자, 상태는 유지하고 기본 정보만 변경한다.
        item.updateInfo(
                request.itemName(),
                request.category(),
                request.description(),
                request.location(),
                request.occurredAt().atStartOfDay()
        );

        // Transaction 안에서 변경된 Entity는 JPA가 자동으로 UPDATE 한다.
        return ItemDetailResponse.from(item);
    }

    /*
     * 시민이 본인이 등록한 분실물의 기본 정보를 수정한다.
     * 분실 중(LOST) 상태인 물품만 수정할 수 있다.
     */
    @Transactional
    public ItemDetailResponse updateLostItem(
            String itemId,
            Integer currentUserId,
            ItemUpdateRequest request
    ) {
        // 로그인한 사용자 정보가 없으면 수정 요청을 처리할 수 없다.
        if (currentUserId == null || currentUserId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        // 수정할 물품이 없으면 기존 상세 조회와 동일하게 404를 반환한다.
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        // 시민 수정 API에서는 분실물만 수정할 수 있다.
        if (item.getItemType() != ItemType.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "분실물만 수정할 수 있습니다."
            );
        }

        // 등록자와 로그인 사용자가 다르면 다른 사람의 글이므로 수정할 수 없다.
        if (!item.getRegistrantId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 등록한 분실물만 수정할 수 있습니다."
            );
        }

        // 반환 절차가 시작됐거나 완료된 분실물은 정보 변경을 막는다.
        if (item.getStatus() != ItemStatus.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "분실 중 상태의 물품만 수정할 수 있습니다."
            );
        }

        // 유형, 등록자, 상태는 유지하고 기본 정보만 변경한다.
        item.updateInfo(
                request.itemName(),
                request.category(),
                request.description(),
                request.location(),
                request.occurredAt().atStartOfDay()
        );

        // Transaction 안에서 변경된 Entity는 JPA가 자동으로 UPDATE 한다.
        return ItemDetailResponse.from(item);
    }

    /*
     * 시민이 본인이 등록한 분실물에 이미지 한 장을 첨부한다.
     * 이미 이미지가 첨부된 물품이거나, 반환 절차가 진행 중인 물품은 첨부할 수 없다.
     *
     * @param itemId        이미지를 첨부할 분실물 번호
     * @param currentUserId 현재 로그인한 시민의 회원 번호
     * @param image         업로드할 이미지 파일 한 장
     * @return 이미지 경로가 연결된 물품 상세 정보
     */
    @Transactional
    public ItemDetailResponse attachLostItemImage(
            String itemId,
            Integer currentUserId,
            MultipartFile image
    ) {
        // 로그인한 사용자 정보가 없으면 이미지 첨부 요청을 처리할 수 없다.
        if (currentUserId == null || currentUserId < 1) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인 사용자 정보가 필요합니다."
            );
        }

        // 이미지 파일을 보내지 않은 요청은 처리하지 않는다.
        if (image == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일은 필수입니다."
            );
        }

        // 대상 물품이 없으면 기존 상세 조회와 동일하게 404를 반환한다.
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));

        // 이미지 첨부 기능은 분실물에만 적용한다.
        if (item.getItemType() != ItemType.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "분실물에만 이미지를 첨부할 수 있습니다."
            );
        }

        // 다른 시민이 등록한 분실물에는 이미지를 첨부할 수 없다.
        if (!item.getRegistrantId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "본인이 등록한 분실물에만 이미지를 첨부할 수 있습니다."
            );
        }

        // 반환 절차가 시작됐거나 완료된 분실물은 이미지를 변경할 수 없다.
        if (item.getStatus() != ItemStatus.LOST) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "분실 중 상태의 물품에만 이미지를 첨부할 수 있습니다."
            );
        }

        // 요구사항상 물품당 이미지는 한 장만 허용한다.
        if (item.getImagePath() != null && !item.getImagePath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지는 1개만 첨부할 수 있습니다."
            );
        }

        // 기존 ItemImageService가 확장자·용량 검증 후 실제 파일을 저장한다.
        String savedImagePath = itemImageService.save(image);

        // 이후 DB 작업이 실패하면 방금 저장한 파일도 함께 삭제한다.
        registerImageCleanupAfterRollback(savedImagePath);

        // DB Entity에 이미지 파일 경로를 연결한다.
        item.attachImage(savedImagePath);

        // Transaction이 끝날 때 JPA가 변경된 imagePath를 자동으로 UPDATE 한다.
        return ItemDetailResponse.from(item);
    }

    /*
     * 등록일 기준으로 기간을 적용한 물품 통계를 조회한다.
     * 시작일과 종료일이 모두 없으면 전체 기간을 집계한다.
     */
    public ItemStatisticsResponse getItemStatistics(
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 시작일이 종료일보다 늦으면 잘못된 기간이므로 400으로 처리한다.
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "통계 시작일은 종료일보다 늦을 수 없습니다."
            );
        }

        // 종료일 다음 날 00:00 미만 조건으로 종료일 하루 전체를 포함한다.
        LocalDateTime startAt = startDate == null
                ? null
                : startDate.atStartOfDay();

        LocalDateTime endAtExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay();

        // 동일 기간의 물품을 한 번만 조회한 뒤 모든 통계를 계산한다.
        List<Item> items = itemRepository.findStatisticsItems(
                startAt,
                endAtExclusive
        );

        // enum의 모든 값이 응답에 항상 포함되도록 먼저 0으로 초기화한다.
        Map<ItemType, Long> itemTypeCounts = new EnumMap<>(ItemType.class);
        for (ItemType itemType : ItemType.values()) {
            itemTypeCounts.put(itemType, 0L);
        }

        Map<ItemStatus, Long> statusCounts = new EnumMap<>(ItemStatus.class);
        for (ItemStatus itemStatus : ItemStatus.values()) {
            statusCounts.put(itemStatus, 0L);
        }

        // 날짜와 월은 시간 순서대로, 카테고리는 이름 기준으로 관리한다.
        Map<String, Long> categoryCountsByName = new TreeMap<>();
        Map<LocalDate, Long> dailyCountsByDate = new TreeMap<>();
        Map<YearMonth, Long> monthlyCountsByMonth = new TreeMap<>();

        for (Item item : items) {
            itemTypeCounts.merge(item.getItemType(), 1L, Long::sum);
            statusCounts.merge(item.getStatus(), 1L, Long::sum);
            categoryCountsByName.merge(item.getCategory(), 1L, Long::sum);

            // 등록 시각(createdAt)을 기준으로 일별·월별 등록 건수를 집계한다.
            LocalDate createdDate = item.getCreatedAt().toLocalDate();
            dailyCountsByDate.merge(createdDate, 1L, Long::sum);
            monthlyCountsByMonth.merge(
                    YearMonth.from(createdDate),
                    1L,
                    Long::sum
            );
        }

        // 카테고리는 건수 내림차순, 같은 건수면 카테고리 이름 오름차순으로 정렬한다.
        List<ItemCategoryCountResponse> categoryCounts =
                categoryCountsByName.entrySet()
                        .stream()
                        .map(entry -> new ItemCategoryCountResponse(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .sorted(
                                Comparator
                                        .comparingLong(
                                                ItemCategoryCountResponse::count
                                        )
                                        .reversed()
                                        .thenComparing(
                                                ItemCategoryCountResponse::category
                                        )
                        )
                        .toList();

        // TreeMap의 날짜 순서를 유지해 일별·월별 차트에 바로 사용할 수 있게 한다.
        List<ItemDailyCountResponse> dailyCounts =
                dailyCountsByDate.entrySet()
                        .stream()
                        .map(entry -> new ItemDailyCountResponse(
                                entry.getKey(),
                                entry.getValue()
                        ))
                        .toList();

        List<ItemMonthlyCountResponse> monthlyCounts =
                monthlyCountsByMonth.entrySet()
                        .stream()
                        .map(entry -> new ItemMonthlyCountResponse(
                                entry.getKey().getYear(),
                                entry.getKey().getMonthValue(),
                                entry.getValue()
                        ))
                        .toList();

        return new ItemStatisticsResponse(
                startDate,
                endDate,
                items.size(),
                itemTypeCounts.get(ItemType.LOST),
                itemTypeCounts.get(ItemType.FOUND),
                statusCounts.get(ItemStatus.HANDOVER_PENDING),
                statusCounts.get(ItemStatus.RETURNED),
                itemTypeCounts,
                statusCounts,
                categoryCounts,
                dailyCounts,
                monthlyCounts
        );
    }
}
