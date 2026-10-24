package com.polink.investigation.service;

import com.polink.investigation.dto.HypothesisChecklist;
import com.polink.investigation.dto.HypothesisChecklist.CheckItem;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// JSON 문자열 변환
@Component
public class HypothesisChecklistConverter {

    private final ObjectMapper objectMapper;

    public HypothesisChecklistConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 추가 확인 사항을 줄별로 나누고 각 항목을 미완료로 설정
    public HypothesisChecklist fromText(String recommendation) {

        List<CheckItem> checkItems = new ArrayList<>();

        if (recommendation == null || recommendation.isBlank()) {
            return new HypothesisChecklist(
                    recommendation,
                    checkItems
            );
        }

        // 줄바꿈 기준 나누기
        String[] lines = recommendation.split("\\R+");

        for (String line : lines) {

            // 줄 앞의 목록 표시 제거 — ex) "- CCTV 확인" → "CCTV 확인"
            String content = line
                    .replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "")
                    .strip();

            // 내용이 없는 줄은 항목으로 만들지 않음
            if (content.isBlank()) {
                continue;
            }

            // 항목 번호는 가설마다 1부터 부여
            checkItems.add(
                    new CheckItem(
                            checkItems.size() + 1,
                            content,
                            false
                    )
            );
        }

        return new HypothesisChecklist(
                recommendation,
                checkItems
        );
    }

    // 체크리스트 객체로 변환
    public HypothesisChecklist read(String storedValue) {

        // 저장된 값이 없을 시 빈 체크리스트로 반환
        if (storedValue == null || storedValue.isBlank()) {
            return fromText(storedValue);
        }

        // 기존에 일반 문장으로 저장된 데이터도 조회 가능
        if (!storedValue.stripLeading().startsWith("{")) {
            return fromText(storedValue);
        }

        try {

            // JSON 문자열을 객체로 변환 — 저장된 체크 상태도 함께 가져오기
            HypothesisChecklist checklist = objectMapper.readValue(
                    storedValue,
                    HypothesisChecklist.class
            );

            // 항목 번호·내용·중복 여부 검사
            validate(checklist);

            // 복원한 체크리스트 반환
            return checklist;

        } catch (JacksonException exception) {
            // JSON 읽기에 실패하면 체크 상태를 임의로 초기화하지 않음
            throw new IllegalStateException(
                    "추가 확인 사항을 불러오지 못했습니다.",
                    exception
            );
        }
    }

    // 체크리스트를 DB 저장용 JSON 문자열로 변환
    public String write(HypothesisChecklist checklist) {

        // 항목 번호·내용·번호 중복 검사
        validate(checklist);

        try {
            // 확인 내용과 체크 상태를 JSON 문자열로 반환
            return objectMapper.writeValueAsString(checklist);

        } catch (JacksonException exception) {
            // 변환 실패 시 원래 오류와 함께 예외 전달
            throw new IllegalStateException(
                    "체크 상태를 저장하지 못했습니다.",
                    exception
            );
        }
    }

    // 체크리스트의 항목 번호·내용·번호 중복 검사
    private void validate(HypothesisChecklist checklist) {

        // 체크리스트나 항목 목록이 없으면 처리 중단
        if (checklist == null || checklist.checkItems() == null) {
            throw new IllegalStateException(
                    "체크리스트 정보를 확인할 수 없습니다."
            );
        }

        // 검사한 항목 번호를 보관해 중복 확인
        Set<Integer> itemIds = new HashSet<>();

        // 항목을 하나씩 꺼내 검사
        for (CheckItem checkItem : checklist.checkItems()) {

            if (checkItem == null
                    || checkItem.itemId() == null
                    || checkItem.itemId() < 1
                    || checkItem.content() == null
                    || checkItem.content().isBlank()
                    || !itemIds.add(checkItem.itemId())) {

                throw new IllegalStateException(
                        "체크리스트 항목 정보를 확인할 수 없습니다."
                );
            }
        }
    }
}


