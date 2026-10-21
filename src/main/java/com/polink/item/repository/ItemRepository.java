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

/** tbl_item의 저장·조회 기능을 Spring Data JPA로 제공한다. */
public interface ItemRepository extends JpaRepository<Item, String> {
    // JpaRepository<Item, String>의 의미
    // 1) Item: 이 Repository가 관리할 Entity
    // 2) String: Item의 PK(itemId) 타입
    // save(), findById(), findAll() 같은 기본 메서드는 Spring Data JPA가 자동으로 만들어 준다.

    /**
     * 분실물(LOST) 또는 습득물(FOUND) 중 번호가 가장 큰 물품 한 건을 찾는다.
     * 예: LOST의 마지막 번호가 L-0115이면 다음 분실물 번호는 L-0116이 된다.
     */
    Optional<Item> findTopByItemTypeOrderByItemIdDesc(ItemType itemType);

    /**
     * 목록 화면의 선택 검색 조건으로 물품을 페이지 단위로 조회한다.
     * 조건 값이 null이면 해당 조건은 적용하지 않는다.
     * Pageable은 page와 size를 받아 필요한 데이터만 조회한다.
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
                """
    )
    Page<Item> findItemsByConditions(
            @Param("itemType") ItemType itemType,
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("status") ItemStatus status,
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

    long countByItemType(ItemType itemType);

    long countByStatus(ItemStatus status);
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
}
