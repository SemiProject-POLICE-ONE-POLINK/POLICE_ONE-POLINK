package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseFile, String> {

    Optional<CaseFile> findFirstByCaseIdStartingWithOrderByCaseIdDesc(
            String prefix);

    Page<CaseFile> findByReporter_UserId(
            Integer userId, Pageable pageable);

    Page<CaseFile> findByAssignedOfficer_UserId(
            Integer userId, Pageable pageable);
}
