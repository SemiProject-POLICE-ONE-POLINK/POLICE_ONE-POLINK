package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class MyCaseListResponse {

    private final List<MyCaseSummaryResponse> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public MyCaseListResponse(Page<CaseFile> page) {
        this.items = page.getContent()
                .stream()
                .map(MyCaseSummaryResponse::new)
                .toList();

        this.page = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.first = page.isFirst();
        this.last = page.isLast();
    }
}
