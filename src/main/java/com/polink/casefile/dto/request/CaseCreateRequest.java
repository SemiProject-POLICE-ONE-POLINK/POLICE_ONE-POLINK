package com.polink.casefile.dto.request;

import com.polink.casefile.entity.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CaseCreateRequest {

    @NotBlank(message = "사건명은 필수입니다.")
    @Size(max = 100, message = "사건명은 100자 이하여야 합니다.")
    private String title;

    @NotNull(message = "사건 유형은 필수입니다.")
    private CaseType caseType;

    @NotBlank(message = "사건 내용은 필수입니다.")
    private String description;

    @NotNull(message = "발생 일시는 필수입니다.")
    private LocalDateTime occurredAt;

    @NotBlank(message = "발생 장소는 필수입니다.")
    @Size(max = 255, message = "발생 장소는 255자 이하여야 합니다.")
    private String location;
}
