package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import com.polink.casefile.entity.CaseStatus;
import com.polink.casefile.entity.CaseType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseFile, String> {

    @Query(
            value = """
                    SELECT c.case_id
                    FROM tbl_case c
                    WHERE c.case_id LIKE CONCAT(:prefix, '%')
                    ORDER BY c.case_id DESC
                    LIMIT 1
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    Optional<String> findLastCaseIdForUpdate(
            @Param("prefix") String prefix);

    Page<CaseFile> findByReporter_UserId(
            Integer userId, Pageable pageable);

    Page<CaseFile> findByAssignedOfficer_UserId(
            Integer userId, Pageable pageable);

    @Query("""
        select c
        from CaseFile c
        where (
            :keyword is null
            or lower(c.caseId) like lower(concat('%', :keyword, '%'))
            or lower(c.title) like lower(concat('%', :keyword, '%'))
            or lower(c.location) like lower(concat('%', :keyword, '%'))
        )
        and (:caseType is null or c.caseType = :caseType)
        and (:status is null or c.status = :status)
        """)
    Page<CaseFile> searchCases(
            @Param("keyword") String keyword,
            @Param("caseType") CaseType caseType,
            @Param("status") CaseStatus status,
            Pageable pageable
    );

    long countByAssignedOfficer_UserIdAndStatus(
            Integer userId,
            CaseStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaseFile c where c.caseId = :caseId")
    Optional<CaseFile> findByCaseIdForUpdate(
            @Param("caseId") String caseId);

    // 시민 마이페이지 전용 - 내가 접수한 사건 전체를 최신 등록순으로 조회
    @Query("""
        select c
        from CaseFile c
        where c.reporter.userId = :userId
        order by c.createdAt desc, c.caseId desc
        """)
    List<CaseFile> findMyCasesForMyPage(@Param("userId") Integer userId);
}
