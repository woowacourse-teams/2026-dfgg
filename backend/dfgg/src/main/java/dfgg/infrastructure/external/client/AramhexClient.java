package dfgg.infrastructure.external.client;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.infrastructure.external.dto.AramhexGames;
import dfgg.infrastructure.external.dto.AramhexLatest;
import dfgg.infrastructure.external.dto.AramhexMeta;
import dfgg.infrastructure.external.dto.AramhexPatchData;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공개 저장소 czgreat/aramhex-data에서 최신 패치의 판 수를 받는다.
 * <p>
 * 해시를 확인한 바이트만 파싱하고, 받기와 파싱의 실패는 {@link AugmentDataRejectedException}으로 끝낸다.
 * 해시와 파일이 같은 저장소에 있어서 전송 손상과 파일 불일치만 잡는다. 저장소에서 함께 바뀐 값은 막지 못한다.
 */
@Component
public class AramhexClient {

    private static final String BASE_URL = "https://raw.githubusercontent.com/czgreat/aramhex-data/main";
    private static final String LATEST_FILE = "latest.json";
    private static final Pattern ID_FORMAT = Pattern.compile("[1-9][0-9]{0,8}");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AramhexClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.objectMapper = strict(objectMapper);
    }

    /** 판 수가 빠지거나 다른 형식이면 0이나 잘린 값으로 바뀌어 틀린 픽률이 조용히 저장되므로 변환을 허용하지 않는다. */
    private static ObjectMapper strict(ObjectMapper objectMapper) {
        ObjectMapper strict = objectMapper.copy()
                .setDefaultSetterInfo(JsonSetter.Value.forContentNulls(Nulls.FAIL))
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true)
                .configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true)
                .registerModule(new SimpleModule().addKeyDeserializer(Integer.class, new IdKeyDeserializer()));
        strict.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        return strict;
    }

    public AramhexPatchData getLatestPatchData() {
        AramhexLatest latest = parse(LATEST_FILE, download(LATEST_FILE, "/" + LATEST_FILE), new TypeReference<>() {
        });
        latest.validate();
        AramhexMeta meta = readVerified(latest, "meta.json", new TypeReference<>() {
        });
        meta.validate(latest);
        Map<Integer, AramhexGames> heroes = readVerified(latest, "heroes.json", new TypeReference<>() {
        });
        Map<Integer, Map<Integer, AramhexGames>> heroAugments = readVerified(latest, "hero_augments.json",
                new TypeReference<>() {
                });
        meta.validateChampionCount(heroes.size());
        meta.validateChampionCount(heroAugments.size());
        return new AramhexPatchData(latest.patch(), meta.provisionalAugmentIds(), heroes, heroAugments);
    }

    private <T> T readVerified(AramhexLatest latest, String file, TypeReference<T> type) {
        byte[] content = download(file, "/patches/{patch}/normal/{file}", latest.patch(), file);
        latest.verify(file, content);
        return parse(file, content, type);
    }

    private byte[] download(String file, String uri, Object... variables) {
        byte[] content = fetch(file, uri, variables);
        if (content == null) {
            throw new AugmentDataRejectedException("aramhex-data 파일이 비어 있습니다: " + file);
        }
        return content;
    }

    private byte[] fetch(String file, String uri, Object... variables) {
        try {
            return restClient.get()
                    .uri(uri, variables)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            throw new AugmentDataRejectedException("aramhex-data 파일을 받지 못했습니다: " + file, exception);
        }
    }

    private <T> T parse(String file, byte[] content, TypeReference<T> type) {
        T value = read(file, content, type);
        if (value == null) {
            throw new AugmentDataRejectedException("aramhex-data 파일 내용이 null입니다: " + file);
        }
        return value;
    }

    private <T> T read(String file, byte[] content, TypeReference<T> type) {
        try {
            return objectMapper.readValue(content, type);
        } catch (IOException exception) {
            throw new AugmentDataRejectedException("aramhex-data 파일을 읽지 못했습니다: " + file, exception);
        }
    }

    /** 기본 변환은 {@code 010}, {@code +10}도 10으로 합쳐 다른 항목을 덮어쓰므로 양수 ID 표기만 받는다. */
    private static final class IdKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext context) throws IOException {
            if (!ID_FORMAT.matcher(key).matches()) {
                return context.handleWeirdKey(Integer.class, key, "숫자 ID 형식이 아닙니다");
            }
            return Integer.valueOf(key);
        }
    }
}
