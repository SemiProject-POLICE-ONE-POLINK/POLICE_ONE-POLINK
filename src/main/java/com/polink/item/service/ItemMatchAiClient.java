package com.polink.item.service;

import com.polink.item.dto.ai.ItemMatchAiCandidate;
import com.polink.item.dto.ai.ItemMatchAiInput;
import java.util.List;

/*
 * 물품 매칭 AI 구현체가 따라야 하는 공통 규칙이다.
 * 현재는 규칙 기반 구현체를 사용할 수 있고,
 * 나중에는 OpenAI API 구현체로 바꿔도 ItemMatchService는 수정하지 않는다.
 */
public interface ItemMatchAiClient {

    /*
     * 분실물 한 건과 서버가 미리 제한한 습득물 후보를 받아
     * 매칭된 습득물 번호와 그 근거를 반환한다.
     *
     * 실제 소유권을 확정하는 기능이 아니라,
     * 반환 신청 전에 보여 주는 참고용 후보만 만든다.
     */
    List<ItemMatchAiCandidate> generateMatches(ItemMatchAiInput input);
}