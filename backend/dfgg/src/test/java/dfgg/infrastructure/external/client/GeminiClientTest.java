package dfgg.infrastructure.external.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dfgg.common.exception.LlmUnavailableException;
import dfgg.common.exception.LlmUnavailableException.Reason;
import dfgg.infrastructure.external.config.GeminiProperties;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

    private static final String BASE_URL = "https://aiplatform.googleapis.com";
    private static final String ENDPOINT = BASE_URL + "/v1/publishers/google/models/test-model:generateContent";
    private static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT");

    private MockRestServiceServer server;

    private GeminiClient clientWithKey(String apiKey) {
        return client(apiKey, "minimal");
    }

    private GeminiClient client(String apiKey, String thinkingLevel) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties(
                apiKey, "test-model", URI.create(BASE_URL), Duration.ofSeconds(5), thinkingLevel);
        return new GeminiClient(builder, properties);
    }

    @Test
    @DisplayName("프롬프트와 응답 스키마를 담아 호출하고 응답 텍스트를 돌려준다")
    void generateJson_WhenResponseHasText_ReturnText() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("추천해줘"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"picks\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        String text = client.generateJson("추천해줘", SCHEMA);

        // then
        assertThat(text).isEqualTo("{\"picks\":[]}");
        server.verify();
    }

    @Test
    @DisplayName("thinking 레벨이 설정되어 있으면 요청에 담는다")
    void generateJson_WhenThinkingLevelSet_SendThinkingConfig() {
        // given
        GeminiClient client = client("test-key", "minimal");
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("minimal"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        client.generateJson("추천해줘", SCHEMA);

        // then
        server.verify();
    }

    @Test
    @DisplayName("thinking 레벨이 비어 있으면 thinkingConfig 필드를 보내지 않는다")
    void generateJson_WhenThinkingLevelBlank_OmitThinkingConfig() {
        // given
        GeminiClient client = client("test-key", "");
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        client.generateJson("추천해줘", SCHEMA);

        // then
        server.verify();
    }

    @Test
    @DisplayName("텍스트가 여러 조각으로 나뉘어 오면 이어 붙여 돌려준다")
    void generateJson_WhenTextSplitIntoParts_JoinParts() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"reasons\\":"},{"text":"[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        String text = client.generateJson("추천해줘", SCHEMA);

        // then
        assertThat(text).isEqualTo("{\"reasons\":[]}");
    }

    @Test
    @DisplayName("thinking 조각은 빼고 답변 조각만 돌려준다")
    void generateJson_WhenThoughtPartIncluded_SkipThoughtPart() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"먼저 생각해 보면","thought":true},{"text":"{}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when
        String text = client.generateJson("추천해줘", SCHEMA);

        // then
        assertThat(text).isEqualTo("{}");
    }

    @Test
    @DisplayName("요청 한도 초과는 사용 불가 예외로 바꾼다")
    void generateJson_WhenTooManyRequests_ThrowUnavailable() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    @DisplayName("서버 오류는 사용 불가 예외로 바꾼다")
    void generateJson_WhenServerError_ThrowUnavailable() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    @DisplayName("응답 대기 시간 초과는 타임아웃 예외로 바꾼다")
    void generateJson_WhenReadTimedOut_ThrowTimeout() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    @DisplayName("후보가 없는 응답은 빈 응답 예외로 바꾼다")
    void generateJson_WhenNoCandidates_ThrowEmpty() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.EMPTY));
    }

    @Test
    @DisplayName("텍스트가 없는 후보는 빈 응답 예외로 바꾼다")
    void generateJson_WhenCandidateHasNoText_ThrowEmpty() {
        // given
        GeminiClient client = clientWithKey("test-key");
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.EMPTY));
    }

    @Test
    @DisplayName("API 키가 없으면 호출하지 않고 사용 불가 예외를 던진다")
    void generateJson_WhenApiKeyBlank_ThrowUnavailableWithoutRequest() {
        // given
        GeminiClient client = clientWithKey("");

        // when & then
        assertThatThrownBy(() -> client.generateJson("추천해줘", SCHEMA))
                .isInstanceOfSatisfying(LlmUnavailableException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.UNAVAILABLE));
        server.verify();
    }
}
