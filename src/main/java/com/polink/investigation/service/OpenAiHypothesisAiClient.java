package com.polink.investigation.service;

import com.polink.investigation.dto.ai.HypothesisAiCandidate;
import com.polink.investigation.dto.ai.HypothesisAiInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;
import java.util.Map;

// 실제 OpenAI 호출 Class
@Component
@Profile("ai-live")
public class OpenAiHypothesisAiClient implements HypothesisAiClient {

    // OpenAI에 HTTP 요청을 보내는 도구
    private final RestClient restClient;

    // Java 객체와 JSON 문자열을 서로 변환하는 도구
    private final ObjectMapper objectMapper;

    // application.yaml에 설정한 모델명: gpt-6-luna
    private final String model;

    private static final Logger log = LoggerFactory.getLogger(OpenAiHypothesisAiClient.class);

    // gpt-6-luna Standard 요금: 토큰 100만 개당 달러
    private static final double INPUT_PRICE = 0.10;
    private static final double OUTPUT_PRICE = 0.50;

    // 원화 표시용 기준 환율. 실시간 환율이 아니므로 필요할 때 갱신
    private static final double USD_TO_KRW = 1323.0;

    public OpenAiHypothesisAiClient(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY}") String apiKey,
            @Value("${app.ai.openai.model}") String model
    ) {

        this.objectMapper = objectMapper;
        this.model = model;

        // OpenAI 서버와 연결을 맺는 데 최대 5초 기다린다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 연결 후 응답을 받는 데 최대 60초 기다린다.
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        // OpenAI 주소와 API 키가 설정된 HTTP 요청 도구를 만들어 보관
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public List<HypothesisAiCandidate> generate(HypothesisAiInput input) {
        String caseData;

        try {
            // 사건·증거물·진술·수사기록을 OpenAI에 보낼 자료를 JSON으로 변환
            caseData = objectMapper.writeValueAsString(input);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 추론을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        }

        // OpenAI에 전달할 모델, 지시문, 사건 자료, 응답 형식을 구성
        Map<String, Object> request = Map.of(
                "model", model,

                // 가설을 만들 때 지켜야 할 규칙
                "instructions", """
                        제공된 사건 정보·증거물·진술·수사기록만 바탕으로
                        서로 다른 수사 가설 후보를 1~3개 제안하세요.
                        
                        확인되지 않은 사실을 만들거나 특정 인물을 범인으로 단정하지 마세요.
                        가설은 '~했을 가능성'처럼 검토가 필요한 표현으로 작성하세요.
                        같은 내용을 말만 바꾸어 여러 후보로 제시하지 마세요.
                        실종 사건에서 범죄 정황이 없다면 범행을 전제하지 마세요.
                        나이·성별·국적·외모 자체를 혐의의 근거로 삼지 마세요.
                        
                        각 후보에 대해 가설을 직접 뒷받침하는 증거물 번호는 evidenceIds,
                        직접 뒷받침하는 진술 번호는 statementIds에 넣으세요.
                        가설과 반대되는 자료의 번호는 opposingEvidenceIds,
                        opposingStatementIds에 구분해 넣으세요.
                        입력 자료에 실제 있는 번호만 사용하고, 관련성이 약한 자료를
                        단지 점수를 높이기 위해 근거 목록에 넣지 마세요.
                        
                        증거물과 진술이 같은 사실을 서로 확인해 주는 경우에만
                        corroborations에 evidenceId, statementId, sharedFact을 기록하세요.
                        같은 사실을 반복한 자료를 별개의 확인 사례로 부풀리지 마세요.
                        정정 전 진술과 정정 후 진술을 동시에 같은 주장의 지지 근거로
                        취급하지 마세요. 불일치 내용은 circumstances에 명시하세요.
                        
                        직접 뒷받침하는 자료가 없으면 해당 근거 목록을 빈 배열로 두고
                        reason에 '직접 뒷받침하는 자료 없음'이라고 적으세요.
                        추가 확인 사항은 additionalRecommendation에 작성하세요.
                        
                        신뢰도 숫자나 퍼센트는 계산하거나 출력하지 마세요.
                        자료 부합도 점수는 서버가 별도로 계산합니다.
                        
                        hypotheses 배열을 포함한 JSON 객체로 답하세요.
                        각 후보에는 content, reason, additionalRecommendation,
                        evidenceIds, statementIds, opposingEvidenceIds,
                        opposingStatementIds, corroborations, circumstances를 포함하세요.
                        해당 항목이 없으면 빈 배열로 작성하세요.
                        """,

                // 앞 단계에서 JSON으로 바꾼 사건·증거물·진술·수사기록
                "input", "결과를 JSON 객체로 반환하세요.\n" + caseData,

                // 응답을 JSON 객체 형태로 요청
                "text", Map.of("format", Map.of("type", "json_object")),

                // OpenAI 쪽에 생성된 응답 객체를 나중에 다시 조회할 수 있도록 저장할지 설정
                "store", false
        );

        Map<?, ?> response;

        try {
            // 준비한 요청을 OpenAI에 보내고 응답 받기
            response = restClient.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

        } catch (RestClientException exception) {
            // 연결 또는 HTTP 요청에 실패하면 오류로 처리
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 추론 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        }

        logUsage(response);

        // 전체 응답에서 AI가 작성한 JSON 문자열만 꺼내기
        String outputText = extractOutputText(response);

        try {
            // {"hypotheses": [...]} JSON을 Java 객체로 변환
            CandidateEnvelope result =
                    objectMapper.readValue(outputText, CandidateEnvelope.class);

            if (result == null) {
                throw invalidResponse();
            }

            // 후보 목록 반환: 내용과 근거 번호는 Service의 Validator가 다시 검사
            return result.hypotheses();
        } catch (JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI 추론 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                    exception
            );
        }
    }


    // OpenAI 응답 전체에서 AI가 작성한 가설 JSON 문자열만 꺼낸다.
    private String extractOutputText(Map<?, ?> response) {

        // OpenAI가 답변 생성을 완료하지 않았으면 결과를 사용하지 않는다.
        if (response == null
                || !"completed".equals(response.get("status"))) {
            throw invalidResponse();
        }

        // 응답에 살펴볼 결과 항목이 있는지 확인
        Object output = response.get("output");
        if (!(output instanceof List<?> outputItems)) {
            throw invalidResponse();
        }

        // 결과 항목 중 실제 답변이 담긴 항목을 찾는다.
        for (Object item : outputItems) {
            if (!(item instanceof Map<?, ?> message)) {
                continue;
            }

            // 이 항목에 AI가 작성한 내용이 없으면 다음 항목을 확인한다.
            Object content = message.get("content");
            if (!(content instanceof List<?> contentItems)) {
                continue;
            }

            // 내용 중 비어 있지 않은 답변 글을 찾아 반환한다.
            for (Object part : contentItems) {
                if (part instanceof Map<?, ?> textPart
                        && "output_text".equals(textPart.get("type"))
                        && textPart.get("text") instanceof String text
                        && !text.isBlank()) {
                    return text;
                }
            }
        }

        // 모든 항목을 확인했음에도 사용할 답변 글이 없을 시 오류 처리
        throw invalidResponse();
    }


    // 완료된 응답에서 사용할 답변을 찾지 못했을 때의 공통 오류
    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 추론 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    // OpenAI 응답의 최상위 형태: {"hypotheses": [가설 후보들]}
    public record CandidateEnvelope(
            List<HypothesisAiCandidate> hypotheses
    ) {
    }

    // OpenAI가 알려준 실제 토큰 수와 해당 요금 기준의 추정 비용을 서버 로그에 기록
    private void logUsage(Map<?, ?> response) {
        if (response == null
                || !(response.get("usage") instanceof Map<?, ?> usage)
                || !(usage.get("input_tokens") instanceof Number input)
                || !(usage.get("output_tokens") instanceof Number output)) {
            log.warn("AI 응답에서 토큰 사용량을 확인할 수 없습니다.");
            return;
        }

        long inputTokens = input.longValue();
        long outputTokens = output.longValue();

        log.info("AI 토큰 사용량: 입력 {}, 출력 {}, 합계 {}",
                inputTokens, outputTokens, inputTokens + outputTokens);

        // 다른 모델로 바꾸면 Luna 가격을 잘못 적용하지 않도록 비용 계산을 건너뛴다.
        if (!"gpt-6-luna".equals(model)) {
            log.warn("모델 {}의 요금이 설정되지 않아 비용을 계산하지 않았습니다.", model);
            return;
        }

        double estimatedUsd =
                (inputTokens * INPUT_PRICE + outputTokens * OUTPUT_PRICE)
                        / 1_000_000.0;
        double estimatedKrw = estimatedUsd * USD_TO_KRW;

        log.info("AI 추정 비용: ${} (약 {}원, 기준 환율 1달러={}원)",
                String.format(Locale.US, "%.6f", estimatedUsd),
                String.format(Locale.US, "%.2f", estimatedKrw),
                USD_TO_KRW);
    }
}