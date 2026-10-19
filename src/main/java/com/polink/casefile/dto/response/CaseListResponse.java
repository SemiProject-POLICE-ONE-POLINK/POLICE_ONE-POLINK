package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class CaseListResponse {

    private final List<CaseSummaryResponse> items;
    private final long totalElements;

    public CaseListResponse(Page<CaseFile> page) {
        this.items = page.getContent()
                .stream()
                .map(caseFile -> new CaseSummaryResponse(caseFile))
                .toList();

        this.totalElements = page.getTotalElements();
    }
}