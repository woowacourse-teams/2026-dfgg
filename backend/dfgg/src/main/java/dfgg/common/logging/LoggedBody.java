package dfgg.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.spi.LoggingEventBuilder;

/** 로그에 남길 요청·응답 본문. JSON은 중첩 객체로, 상한을 넘으면 잘린 문자열로 남기고 puuid는 앞 4자만 둔다. */
public final class LoggedBody {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggedBody EMPTY = new LoggedBody(null, false);
    private static final String PUUID_KEY = "puuid";
    private static final int PUUID_VISIBLE_LENGTH = 4;
    private static final String MASK_SUFFIX = "…";
    // 잘린 문자열 안의 puuid. 닫는 따옴표가 잘려 나간 경우도 잡는다.
    private static final Pattern PUUID_IN_TEXT = Pattern.compile("(\"puuid\"\\s*:\\s*\")([^\"]{0,4})[^\"]*");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final Object logValue;
    private final boolean truncated;

    private LoggedBody(Object logValue, boolean truncated) {
        this.logValue = logValue;
        this.truncated = truncated;
    }

    public static LoggedBody of(byte[] content, int maxBytes) {
        if (content.length == 0) {
            return EMPTY;
        }
        if (content.length > maxBytes) {
            String head = new String(Arrays.copyOf(content, maxBytes), StandardCharsets.UTF_8);
            return new LoggedBody(maskPuuidInText(sanitize(head)), true);
        }
        return new LoggedBody(parse(content), false);
    }

    /** {@code key}와, 잘렸다면 {@code key_truncated}를 붙인다. */
    public void addTo(LoggingEventBuilder event, String key) {
        if (isEmpty()) {
            return;
        }
        event.addKeyValue(key, logValue);
        if (truncated) {
            event.addKeyValue(key + "_truncated", true);
        }
    }

    public boolean isEmpty() {
        return logValue == null;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public Object logValue() {
        return logValue;
    }

    private static Object parse(byte[] content) {
        try {
            return maskPuuid(OBJECT_MAPPER.readValue(content, Object.class));
        } catch (IOException notJson) {
            return sanitize(new String(content, StandardCharsets.UTF_8));
        }
    }

    private static Object maskPuuid(Object node) {
        if (node instanceof Map<?, ?> map) {
            return maskPuuidInObject(map);
        }
        if (node instanceof List<?> list) {
            return list.stream().map(LoggedBody::maskPuuid).toList();
        }
        return node;
    }

    private static Map<Object, Object> maskPuuidInObject(Map<?, ?> map) {
        Map<Object, Object> masked = new LinkedHashMap<>();
        map.forEach((key, value) -> masked.put(key, maskEntry(key, value)));
        return masked;
    }

    private static Object maskEntry(Object key, Object value) {
        if (PUUID_KEY.equals(key) && value instanceof String puuid) {
            return mask(puuid);
        }
        return maskPuuid(value);
    }

    private static String mask(String puuid) {
        return puuid.substring(0, Math.min(PUUID_VISIBLE_LENGTH, puuid.length())) + MASK_SUFFIX;
    }

    private static String maskPuuidInText(String text) {
        return PUUID_IN_TEXT.matcher(text).replaceAll("$1$2" + MASK_SUFFIX);
    }

    // 사용자가 보낸 값이다. 줄바꿈이 남으면 로그 한 줄을 위조할 수 있다.
    private static String sanitize(String text) {
        return CONTROL_CHARACTERS.matcher(text).replaceAll("?");
    }
}
