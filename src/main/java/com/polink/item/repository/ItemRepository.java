package com.polink.item.repository;

import com.polink.item.entity.Item;
import com.polink.item.entity.ItemStatus;
import com.polink.item.entity.ItemType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.polink.item.dto.ItemCategoryCountResponse;
import java.util.List;
import java.time.LocalDateTime;

/** tbl_item의 저장·조회 기능을 Spring Data JPA로 제공한다. */
public interface ItemRepository extends JpaRepository<Item, String> {
    // JpaRepository<Item, String>의 의미
    // 1) Item: 이 Repository가 관리할 Entity
    // 2) String: Item의 PK(itemId) 타입
    // save(), findById(), findAll() 같은 기본 메서드는 Spring Data JPA가 자동으로 만들어 준다.

    /*
     * 분실물(LOST) 또는 습득물(FOUND) 중 번호가 가장 큰 물품 한 건을 찾는다.
     * 예: LOST의 마지막 번호가 L-0115이면 다음 분실물 번호는 L-0116이 된다.
     */
    Optional<Item> findTopByItemTypeOrderByItemIdDesc(ItemType itemType);

    /*
     * 목록 화면의 선택 검색 조건으로 물품을 페이지 단위로 조회한다.
     * 조건 값이 null이면 해당 조건은 적용하지 않는다.
     * location은 포함 검색이고,
     * startDate는 발생일의 시작 시각,
     * endDateExclusive는 종료일 다음 날 00:00이다.
     * 종료일 다음 날보다 "작은" 조건을 사용해 종료일 하루 전체를 포함한다.
     */
    @Query(
            value = """
                select item
                from Item item
                where (:itemType is null or item.itemType = :itemType)
                  and (:category is null or lower(item.category) = lower(:category))
                  and (:status is null or item.status = :status)
                  and (:keyword is null
                       or lower(item.itemName) like concat('%', lower(:keyword), '%')
                       or lower(item.description) like concat('%', lower(:keyword), '%'))
                  and (:location is null
                       or lower(item.location) like concat('%', lower(:location), '%'))
                  and (:startDate is null or item.occurredAt >= :startDate)
                  and (:endDateExclusive is null or item.occurredAt < :endDateExclusive)
                order by item.createdAt desc, item.itemId desc
                """,
            countQuery = """
                select count(item)
                from Item item
                where (:itemType is null or item.itemType = :itemType)
                  and (:category is null or lower(item.category) = lower(:category))
                  and (:status is null or item.status = :status)
                  and (:keyword is null
                       or lower(item.itemName) like concat('%', lower(:keyword), '%')
                       or lower(item.description) like concat('%', lower(:keyword), '%'))
                  and (:location is null
                       or lower(item.location) like concat('%', lower(:location), '%'))
                  and (:startDate is null or item.occurredAt >= :startDate)
                  and (:endDateExclusive is null or item.occurredAt < :endDateExclusive)
                """
    )
    Page<Item> findItemsByConditions(
            @Param("itemType") ItemType itemType,
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("location") String location,
            @Param("status") ItemStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive,
            Pageable pageable
    );

    /*
     * 특정 등록자(registrantId)가 등록한 물품만 최신 등록순으로 페이지 조회한다.
     * 로그인 연동 후에는 Controller가 로그인한 사용자의 userId를 전달한다.
     */
    Page<Item> findByRegistrantIdOrderByCreatedAtDescItemIdDesc(
            Integer registrantId,
            Pageable pageable
    );

    /*
     * 로그인한 시민의 "내 물품" 화면용 조회다.
     * <p>일반 목록 조회와 달리 {@code registrantId} 조건은 항상 적용한다.
     * 따라서 요청 파라미터를 조작해 다른 시민의 등록 물품을 가져올 수 없다.
     * {@code itemType}, {@code status}는 선택 필터이므로 값이 없으면 전체를 반환한다.</p>
     */
    @Query(
            value = """
                select item
                from Item item
                where item.registrantId = :registrantId
                  and (:itemType is null or item.itemType = :itemType)
                  and (:status is null or item.status = :status)
                order by item.createdAt desc, item.itemId desc
                """,
            countQuery = """
                select count(item)
                from Item item
                where item.registrantId = :registrantId
                  and (:itemType is null or item.itemType = :itemType)
                  and (:status is null or item.status = :status)
                """
    )
    Page<Item> findMyItemsByConditions(
            @Param("registrantId") Integer registrantId,
            @Param("itemType") ItemType itemType,
            @Param("status") ItemStatus status,
            Pageable pageable
    );

    /**
     * AI 물품 매칭에 사용할 습득물 후보를 조회한다.
     * 반환이 끝난 물품은 후보가 될 수 없으므로,
     * FOUND 유형이면서 보관 중(STORED)인 물품만 조회한다.
     * 최신 등록 물품부터 조회한 뒤 Service에서 유사도 점수순으로 다시 정렬한다.
     */
    List<Item> findByItemTypeAndStatusOrderByCreatedAtDescItemIdDesc(
            ItemType itemType,
            ItemStatus status
    );

    @Query("""
        select new com.polink.item.dto.ItemCategoryCountResponse(
            item.category,
            count(item)
        )
        from Item item
        group by item.category
        order by count(item) desc, item.category asc
        """)
    List<ItemCategoryCountResponse> countItemsByCategory();

    /*
     * 통계 조회 기간에 포함되는 물품을 모두 조회한다.
     * startAt 또는 endAtExclusive가 null이면 해당 날짜 조건은 적용하지 않는다.
     * endAtExclusive는 종료일 다음 날 00:00이므로 종료일 하루 전체를 포함할 수 있다.
     */
    @Query("""
        select item
        from Item item
        where (:startAt is null or item.createdAt >= :startAt)
          and (:endAtExclusive is null or item.createdAt < :endAtExclusive)
        order by item.createdAt asc, item.itemId asc
        """)
    List<Item> findStatisticsItems(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAtExclusive") LocalDateTime endAtExclusive
    );
}
