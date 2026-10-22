package dfgg.infrastructure.external.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.infrastructure.external.config.LeaguepediaProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Leaguepedia(MediaWiki) API 클라이언트. 봇 비밀번호로 로그인하고, 받은 세션 쿠키를 이후 요청에 싣는다.
 * 모든 요청 사이에 설정한 간격을 두고, {@code ratelimited}·{@code maxlag}는 두 배씩 늘어나는 백오프로 다시 시도한다.
 * 비밀번호·쿠키는 로그나 예외 메시지에 넣지 않는다.
 */
@Component
public class LeaguepediaClient {

    private static final String API_PATH = "/api.php";
    private static final String LOGIN_SUCCESS = "Success";
    // apihighlimits가 없는 봇 계정의 최대 행 수
    private static final int PAGE_SIZE = 500;
    private static final String MAX_LAG_SECONDS = "5";
    private static final Set<String> THROTTLED_CODES = Set.of("ratelimited", "maxlag");

    private final RestClient restClient;
    private final LeaguepediaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> cookies = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Sleeper sleeper;
    private Instant lastRequestAt;

    @Autowired
    public LeaguepediaClient(RestClient.Builder builder, LeaguepediaProperties properties) {
        this(builder, properties, Clock.systemUTC(), LeaguepediaClient::sleepThread);
    }

    LeaguepediaClient(RestClient.Builder builder, LeaguepediaProperties properties, Clock clock, Sleeper sleeper) {
        this.restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public void login() {
        if (!properties.hasCredentials()) {
            throw new LeaguepediaException("LEAGUEPEDIA_BOT_USERNAME·LEAGUEPEDIA_BOT_PASSWORD가 설정되지 않았습니다.");
        }
        String token = loginToken();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("action", "login");
        form.add("lgname", properties.botUsername());
        form.add("lgpassword", properties.botPassword());
        form.add("lgtoken", token);
        form.add("format", "json");
        JsonNode result = post(form).path("login");
        if (!LOGIN_SUCCESS.equals(result.path("result").asText())) {
            throw new LeaguepediaException("Leaguepedia 로그인 실패: " + result.path("result").asText()
                    + " (" + result.path("reason").asText() + ")");
        }
    }

    /** 조건에 맞는 행을 모두 받는다. 한 페이지가 꽉 차면(500행) offset을 늘려 다음 페이지를 이어 받는다. */
    public List<Map<String, String>> cargoQuery(CargoQuery query) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<Map<String, String>> page;
        do {
            page = cargoPage(query, rows.size());
            rows.addAll(page);
        } while (page.size() == PAGE_SIZE);
        return rows;
    }

    private List<Map<String, String>> cargoPage(CargoQuery query, int offset) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "cargoquery");
        params.put("tables", query.tables());
        params.put("fields", query.fields());
        params.put("where", query.where());
        params.put("order_by", query.orderBy());
        params.put("limit", String.valueOf(PAGE_SIZE));
        params.put("offset", String.valueOf(offset));
        params.put("maxlag", MAX_LAG_SECONDS);
        JsonNode body = getWithRetry(params);
        if (body.has("error")) {
            throw new LeaguepediaException("Leaguepedia cargoquery 실패: " + errorCodeOf(body));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        body.path("cargoquery").forEach(row -> rows.add(fieldsOf(row.path("title"))));
        return rows;
    }

    private Map<String, String> fieldsOf(JsonNode title) {
        Map<String, String> fields = new LinkedHashMap<>();
        title.properties().forEach(field -> fields.put(field.getKey(), field.getValue().asText()));
        return fields;
    }

    private String loginToken() {
        JsonNode body = getWithRetry(Map.of("action", "query", "meta", "tokens", "type", "login"));
        String token = body.path("query").path("tokens").path("logintoken").asText();
        if (token.isEmpty()) {
            throw new LeaguepediaException("Leaguepedia 로그인 토큰을 받지 못했습니다: " + errorCodeOf(body));
        }
        return token;
    }

    private JsonNode getWithRetry(Map<String, String> params) {
        JsonNode body = get(params);
        for (int retry = 0; isThrottled(body); retry++) {
            if (retry == properties.maxRetries()) {
                throw new LeaguepediaException("Leaguepedia 재시도 " + retry + "회를 넘겼습니다: " + errorCodeOf(body));
            }
            sleeper.sleep(properties.retryBackoff().multipliedBy(1L << retry));
            body = get(params);
        }
        return body;
    }

    private boolean isThrottled(JsonNode body) {
        return THROTTLED_CODES.contains(body.path("error").path("code").asText());
    }

    private JsonNode get(Map<String, String> params) {
        waitForTurn();
        ResponseEntity<String> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path(API_PATH);
                    params.forEach(uriBuilder::queryParam);
                    return uriBuilder.queryParam("format", "json").build();
                })
                .headers(this::addCookies)
                .retrieve()
                .toEntity(String.class);
        return read(response);
    }

    private JsonNode post(MultiValueMap<String, String> form) {
        waitForTurn();
        ResponseEntity<String> response = restClient.post()
                .uri(API_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(this::addCookies)
                .body(form)
                .retrieve()
                .toEntity(String.class);
        return read(response);
    }

    // 직전 요청에서 간격이 다 지나지 않았으면 남은 만큼 기다린다.
    private synchronized void waitForTurn() {
        if (lastRequestAt != null) {
            Duration remaining = properties.requestInterval().minus(Duration.between(lastRequestAt, clock.instant()));
            if (remaining.isPositive()) {
                sleeper.sleep(remaining);
            }
        }
        lastRequestAt = clock.instant();
    }

    private static void sleepThread(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LeaguepediaException("Leaguepedia 요청 대기 중 중단되었습니다.");
        }
    }

    private void addCookies(HttpHeaders headers) {
        if (cookies.isEmpty()) {
            return;
        }
        headers.set(HttpHeaders.COOKIE, cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; ")));
    }

    private JsonNode read(ResponseEntity<String> response) {
        storeCookies(response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        try {
            return objectMapper.readTree(response.getBody());
        } catch (JsonProcessingException exception) {
            throw new LeaguepediaException("Leaguepedia 응답이 JSON이 아닙니다.");
        }
    }

    // "이름=값; path=/; ..."에서 이름과 값만 남긴다.
    private void storeCookies(List<String> setCookies) {
        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int separator = pair.indexOf('=');
            if (separator > 0) {
                cookies.put(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
            }
        }
    }

    private String errorCodeOf(JsonNode body) {
        return body.path("error").path("code").asText("응답에 토큰 없음");
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(Duration duration);
    }
}
