package com.polink.casefile.repository;

import com.polink.casefile.entity.CaseClose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCloseRepository
        extends JpaRepository<CaseClose, String> {
}
