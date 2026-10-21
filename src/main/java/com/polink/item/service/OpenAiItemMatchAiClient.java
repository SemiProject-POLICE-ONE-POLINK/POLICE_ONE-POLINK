package com.polink.item.service;

import com.polink.item.dto.ai.ItemMatchAiCandidate;
import com.polink.item.dto.ai.ItemMatchAiInput;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/*
 * ai-live 프로필에서만 실제 OpenAI Responses API를 호출하는 구현체다.
 *
 * API 오류, 네트워크 오류, 잘못된 AI 응답이 발생하면
 * 기존 규칙 기반 매칭으로 자동 대체한다.
 */
@Component
@Profile("item-ai-live")
public class OpenAiItemMatchAiClient implements ItemMatchAiClient {

    // OpenAI API 통신과 JSON 변환에 사용하는 객체다.
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // 환경 변수 OPENAI_ITEM_MATCH_MODEL에서 읽은 모델 이름이다.
    private final String model;

    // 외부 API 호출 실패 시 기존 규칙 기반 결과를 제공한다.
    private final RuleBasedItemMatchAiClient fallbackClient =
            new RuleBasedItemMatchAiClient();

    private static final Logger log =
            LoggerFactory.getLogger(OpenAiItemMatchAiClient.class);

    public OpenAiItemMatchAiClient(
            ObjectMapper objectMapper,

            // API 키는 코드나 application.yml이 아닌 환경 변수로만 받는다.
            @Value("${OPENAI_API_KEY}") String apiKey,

            // 사용 모델도 환경 변수로 설정해 코드 변경 없이 교체할 수 있게 한다.
            @Value("${OPENAI_ITEM_MATCH_MODEL}") String model
    ) {
        this.objectMapper = objectMapper;
        this.model = model;

        // OpenAI 서버 연결은 최대 5초까지만 기다린다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 모델 응답 생성은 시간이 걸릴 수 있어 최대 30초까지 기다린다.
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        // API 주소와 Authorization 헤더를 공통으로 설정한다.
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /*
     * 서버가 준비한 분실물·습득물 후보를 OpenAI에 전달한다.
     * 반환값은 반드시 Service에서 실제 후보 번호인지 다시 검증한다.
     */
    @Override
    public List<ItemMatchAiCandidate> generateMatches(ItemMatchAiInput input) {
        try {
            // Entity가 아닌 최소 정보 DTO만 JSON으로 변환해 개인정보 노출을 줄인다.
            String itemData = objectMapper.writeValueAsString(input);

            Map<String, Object> request = Map.of(
                    "model", model,

                    // AI가 지켜야 할 업무·안전 규칙이다.
                    "instructions", """
                            당신은 분실물과 습득물의 후보 매칭을 돕는 보조 시스템입니다.

                            제공된 분실물 정보와 습득물 후보 목록만 사용하세요.
                            입력에 없는 물품 번호를 새로 만들지 마세요.
                            실제 소유자라고 단정하거나 반환 완료를 지시하지 마세요.
                            물품명, 카테고리, 설명, 장소, 발생 시각이 유사한 후보만 선택하세요.

                            반드시 아래 JSON 형식으로만 답하세요.

                            {
                              "matches": [
                                {
                                  "foundItemId": "F-0001",
                                  "reason": "물품 특징과 장소 정보가 유사합니다."
                                }
                              ]
                            }

                            매칭 후보가 없으면 matches는 빈 배열로 반환하세요.
                            """,

                    // 서버가 검증한 최소 물품 정보다.
                    "input", itemData,

                    // 문자열이 아닌 JSON 객체 응답을 요청한다.
                    "text", Map.of(
                            "format",
                            Map.of("type", "json_object")
                    ),

                    // OpenAI 서버에 응답 데이터를 저장하지 않는다.
                    "store", false
            );

            // OpenAI Responses API를 호출한다.
            Map<?, ?> response = restClient.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            // 응답 전체에서 AI가 작성한 JSON 문자열만 추출한다.
            String outputText = extractOutputText(response);

            // AI JSON을 Java 후보 목록으로 변환한다.
            CandidateEnvelope result = objectMapper.readValue(
                    outputText,
                    CandidateEnvelope.class
            );

            if (result == null || result.matches() == null) {
                throw new IllegalArgumentException("AI 응답 형식이 올바르지 않습니다.");
            }

            // 실제 OpenAI 응답을 정상적으로 받았는지 서버 로그에서 확인할 수 있게 기록한다.
            log.info(
                    "OpenAI 물품 매칭 성공: AI가 반환한 후보 {}건",
                    result.matches().size()
            );

            // 후보 번호·중복 여부는 ItemMatchService가 최종 검증한다.
            return result.matches();

        } catch (
                RestClientException
                | JacksonException
                | IllegalArgumentException exception
        ) {
            // 외부 AI가 실패해도 기존 매칭 기능은 멈추지 않게 한다.
            log.warn(
                    "OpenAI 물품 매칭에 실패하여 규칙 기반 매칭으로 대체합니다.",
                    exception
            );

            return fallbackClient.generateMatches(input);
        }
    }

    /*
     * OpenAI Responses API의 전체 응답에서
     * 실제 모델이 작성한 output_text 문자열만 찾는다.
     */
    private String extractOutputText(Map<?, ?> response) {
        if (response == null
                || !"completed".equals(response.get("status"))) {
            throw new IllegalArgumentException("OpenAI 응답이 완료되지 않았습니다.");
        }

        Object output = response.get("output");
        if (!(output instanceof List<?> outputItems)) {
            throw new IllegalArgumentException("OpenAI 응답 본문이 없습니다.");
        }

        for (Object item : outputItems) {
            if (!(item instanceof Map<?, ?> message)) {
                continue;
            }

            Object content = message.get("content");
            if (!(content instanceof List<?> contentItems)) {
                continue;
            }

            for (Object part : contentItems) {
                if (part instanceof Map<?, ?> textPart
                        && "output_text".equals(textPart.get("type"))
                        && textPart.get("text") instanceof String text
                        && !text.isBlank()) {
                    return text;
                }
            }
        }

        throw new IllegalArgumentException("AI 응답에서 매칭 결과를 찾을 수 없습니다.");
    }

    /*
     * OpenAI가 반환해야 하는 최상위 JSON 형태다.
     * 예: { "matches": [ ... ] }
     */
    private record CandidateEnvelope(
            List<ItemMatchAiCandidate> matches
    ) {
    }
}