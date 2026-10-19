package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
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
}