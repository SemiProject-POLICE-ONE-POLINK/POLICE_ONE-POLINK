package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseRepository extends JpaRepository<CaseFile, String> {

    Optional<CaseFile> findFirstByCaseIdStartingWithOrderByCaseIdDesc(
            String prefix);
}
