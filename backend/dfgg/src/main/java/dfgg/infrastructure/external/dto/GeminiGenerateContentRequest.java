package dfgg.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public record GeminiGenerateContentRequest(List<Content> contents, GenerationConfig generationConfig) {

    private static final String USER_ROLE = "user";
    private static final String JSON_MIME_TYPE = "application/json";

    /** 응답을 주어진 스키마의 JSON으로 강제하는 단일 턴 요청. thinking 레벨은 모델 기본값을 쓴다. */
    public static GeminiGenerateContentRequest jsonResponse(String prompt, Map<String, Object> responseSchema) {
        return jsonResponse(prompt, responseSchema, null);
    }

    /** 응답을 주어진 스키마의 JSON으로 강제하고, thinking을 주어진 레벨로 제한하는 단일 턴 요청. */
    public static GeminiGenerateContentRequest jsonResponse(
            String prompt, Map<String, Object> responseSchema, ThinkingConfig thinkingConfig) {
        return new GeminiGenerateContentRequest(
                List.of(new Content(USER_ROLE, List.of(new Part(prompt)))),
                new GenerationConfig(JSON_MIME_TYPE, responseSchema, thinkingConfig)
        );
    }

    public record Content(String role, List<Part> parts) {
    }

    public record Part(String text) {
    }

    /** {@code thinkingConfig}가 없으면 필드째 뺀다. null을 보내면 API가 거부할 수 있다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
            String responseMimeType, Map<String, Object> responseSchema, ThinkingConfig thinkingConfig) {
    }

    public record ThinkingConfig(String thinkingLevel) {
    }
}
