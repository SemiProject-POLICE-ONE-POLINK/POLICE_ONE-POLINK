package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class MyCaseListResponse {

    private final List<MyCaseSummaryResponse> items;
    private final long totalElements;

    public MyCaseListResponse(Page<CaseFile> page) {
        this.items = page.getContent()
                .stream()
                .map(MyCaseSummaryResponse::new)
                .toList();

        this.totalElements = page.getTotalElements();
    }
}
