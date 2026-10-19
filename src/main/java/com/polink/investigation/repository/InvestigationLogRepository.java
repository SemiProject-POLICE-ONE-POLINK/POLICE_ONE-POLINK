package com.polink.investigation.repository;

import com.polink.investigation.entity.InvestigationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationLogRepository extends JpaRepository<InvestigationLog, Integer> {
}
