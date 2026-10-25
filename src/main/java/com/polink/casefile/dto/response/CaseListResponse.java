package com.polink.casefile.dto.response;

import com.polink.casefile.entity.CaseFile;
import com.polink.user.entity.UserRole;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class CaseListResponse {

    private final List<CaseSummaryResponse> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;

    public CaseListResponse(
            Page<CaseFile> page,
            Integer viewerId,
            UserRole viewerRole
    ) {
        this.items = page.getContent()
                .stream()
                .map(caseFile -> new CaseSummaryResponse(
                        caseFile,
                        viewerId,
                        viewerRole
                ))
                .toList();

        this.page = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.first = page.isFirst();
        this.last = page.isLast();
    }
}
