package com.polink.casefile.service;

import com.polink.casefile.dto.ai.SimilarCaseAiInput;
import com.polink.casefile.dto.ai.SimilarCaseAiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Profile("ai-live")
public class OpenAiSimilarCaseAiClient implements SimilarCaseAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiSimilarCaseAiClient(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY}") String apiKey,
            @Value("${app.ai.openai.model}") String model
    ) {
        this.objectMapper = objectMapper;
        this.model = model;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey
                )
                .build();
    }

    @Override
    public SimilarCaseAiResult analyze(SimilarCaseAiInput input) {
        String inputJson;

        try {
            inputJson = objectMapper.writeValueAsString(input);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 사건 분석을 시작하지 못했습니다.",
                    exception
            );
        }

        Map<String, Object> request = Map.of(
                "model", model,
                "instructions", """
                        시민이 입력한 상황을 분석하고 유사 사건을 찾으세요.

                        suggestedCaseType은 반드시 다음 중 하나여야 합니다.
                        THEFT, FRAUD, MISSING, CYBER, ETC

                        matches에는 입력 candidates에 실제 존재하는 caseId만
                        최대 3개까지 포함하세요. 유사한 사건이 없으면 빈 배열로
                        반환하세요. 입력에 없는 사건이나 사실을 만들지 마세요.

                        analysis에는 상황에 대한 간단한 분석을 작성하세요.
                        recommendedAction에는 시민이 증거를 보존하거나 신고를
                        준비하기 위해 할 수 있는 일반적인 조치를 작성하세요.
                        범죄 여부나 특정인의 유죄를 단정하지 마세요.

                        다음 필드를 포함하는 JSON 객체로만 답하세요.
                        analysis, suggestedCaseType, recommendedAction, matches

                        matches의 각 항목에는 caseId와 similarityReason을
                        포함하세요.
                        """,
                "input", "분석할 입력 데이터입니다.\n" + inputJson,
                "text", Map.of(
                        "format",
                        Map.of("type", "json_object")
                ),
                "store", false
        );

        Map<?, ?> response;

        try {
            response = restClient.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 사건 분석 중 문제가 발생했습니다.",
                    exception
            );
        }

        String outputText = extractOutputText(response);

        try {
            SimilarCaseAiResult result = objectMapper.readValue(
                    outputText,
                    SimilarCaseAiResult.class
            );

            if (result == null) {
                throw invalidResponse();
            }

            return result;
        } catch (JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 사건 분석 결과를 처리하지 못했습니다.",
                    exception
            );
        }
    }

    private String extractOutputText(Map<?, ?> response) {
        if (response == null
                || !"completed".equals(response.get("status"))) {
            throw invalidResponse();
        }

        Object output = response.get("output");

        if (!(output instanceof List<?> outputItems)) {
            throw invalidResponse();
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

        throw invalidResponse();
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 사건 분석 결과를 받지 못했습니다."
        );
    }
}
