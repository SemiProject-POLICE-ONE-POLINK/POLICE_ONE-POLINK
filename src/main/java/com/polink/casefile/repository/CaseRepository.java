package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaseFile c where c.caseId = :caseId")
    Optional<CaseFile> findByCaseIdForUpdate(
            @Param("caseId") String caseId);
}
