package com.polink.investigation.repository;

import com.polink.investigation.entity.Hypothesis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HypothesisRepository extends JpaRepository<Hypothesis, Integer> {

    // 해당 사건에서 채택된 가설을 최신 채택순으로 조회
    @Query("""
            SELECT h
            FROM Hypothesis h
            WHERE h.caseFile.caseId = :caseId
            ORDER BY h.adoptedAt DESC, h.hypothesisId DESC
            """)
    List<Hypothesis> findAdoptedByCaseId(@Param("caseId") String caseId);
}
