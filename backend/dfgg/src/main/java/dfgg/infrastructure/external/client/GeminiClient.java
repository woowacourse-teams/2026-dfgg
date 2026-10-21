package dfgg.infrastructure.external.client;

import dfgg.common.exception.LlmUnavailableException;
import dfgg.common.exception.LlmUnavailableException.Reason;
import dfgg.infrastructure.external.config.GeminiProperties;
import dfgg.infrastructure.external.dto.GeminiGenerateContentRequest;
import dfgg.infrastructure.external.dto.GeminiGenerateContentResponse;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gemini {@code generateContent}를 호출한다. 실패는 전부 {@link LlmUnavailableException}으로 바꾼다.
 */
public class GeminiClient {

    private static final String API_KEY_HEADER = "x-goog-api-key";
    /** Vertex AI(Agent Platform) API 키 방식 경로. 프로젝트·리전 없이 전역 엔드포인트로 호출한다. */
    private static final String GENERATE_CONTENT_PATH = "/v1/publishers/google/models/{model}:generateContent";

    private final RestClient restClient;
    private final GeminiProperties properties;

    public GeminiClient(RestClient.Builder builder, GeminiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .build();
        this.properties = properties;
    }

    /** 응답을 {@code responseSchema} 형태의 JSON으로 강제하고, 그 JSON 문자열을 돌려준다. */
    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        if (!properties.hasCredentials()) {
            throw new LlmUnavailableException(Reason.UNAVAILABLE, "Gemini API 키나 모델이 설정되지 않았습니다");
        }
        GeminiGenerateContentResponse response = call(request(prompt, responseSchema));
        if (response == null) {
            throw new LlmUnavailableException(Reason.EMPTY, "Gemini 응답 본문이 비어 있습니다");
        }
        return response.firstText()
                .orElseThrow(() -> new LlmUnavailableException(Reason.EMPTY, "Gemini 응답에 텍스트가 없습니다"));
    }

    private GeminiGenerateContentRequest request(String prompt, Map<String, Object> responseSchema) {
        if (!properties.hasThinkingLevel()) {
            return GeminiGenerateContentRequest.jsonResponse(prompt, responseSchema);
        }
        return GeminiGenerateContentRequest.jsonResponse(prompt, responseSchema,
                new GeminiGenerateContentRequest.ThinkingConfig(properties.thinkingLevel()));
    }

    private GeminiGenerateContentResponse call(GeminiGenerateContentRequest request) {
        try {
            return restClient.post()
                    .uri(GENERATE_CONTENT_PATH, properties.model())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);
        } catch (ResourceAccessException exception) {
            throw new LlmUnavailableException(accessFailureReason(exception), "Gemini 호출에 실패했습니다", exception);
        } catch (RestClientException exception) {
            throw new LlmUnavailableException(Reason.UNAVAILABLE, "Gemini 호출에 실패했습니다", exception);
        }
    }

    private Reason accessFailureReason(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
            return Reason.TIMEOUT;
        }
        return Reason.UNAVAILABLE;
    }
}
