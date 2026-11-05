package dfgg.infrastructure.external.client;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.infrastructure.external.dto.AramhexGames;
import dfgg.infrastructure.external.dto.AramhexLatest;
import dfgg.infrastructure.external.dto.AramhexMeta;
import dfgg.infrastructure.external.dto.AramhexPatchData;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공개 저장소 czgreat/aramhex-data에서 최신 패치의 판 수를 받는다.
 * <p>
 * 개인 저장소의 파일이라 믿지 않는다. 해시를 먼저 확인한 바이트만 파싱하고, 어떤 실패든 {@link AugmentDataRejectedException}으로 끝낸다.
 */
@Component
public class AramhexClient {

    private static final String BASE_URL = "https://raw.githubusercontent.com/czgreat/aramhex-data/main";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AramhexClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper.copy()
                .setDefaultSetterInfo(JsonSetter.Value.forContentNulls(Nulls.FAIL));
    }

    public AramhexPatchData getLatestPatchData() {
        AramhexLatest latest = parse(download("/latest.json"), new TypeReference<>() {
        });
        latest.validate();
        AramhexMeta meta = parse(downloadVerified(latest, "meta.json"), new TypeReference<>() {
        });
        meta.validate();
        Map<Integer, AramhexGames> heroes = parse(downloadVerified(latest, "heroes.json"), new TypeReference<>() {
        });
        Map<Integer, Map<Integer, AramhexGames>> heroAugments = parse(
                downloadVerified(latest, "hero_augments.json"), new TypeReference<>() {
                });
        return new AramhexPatchData(latest.patch(), heroes, heroAugments);
    }

    private byte[] downloadVerified(AramhexLatest latest, String file) {
        byte[] content = download("/patches/{patch}/normal/{file}", latest.patch(), file);
        latest.verify(file, content);
        return content;
    }

    private byte[] download(String uri, Object... variables) {
        byte[] content = fetch(uri, variables);
        if (content == null) {
            throw new AugmentDataRejectedException("aramhex-data 파일이 비어 있습니다: " + uri);
        }
        return content;
    }

    private byte[] fetch(String uri, Object... variables) {
        try {
            return restClient.get()
                    .uri(uri, variables)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            throw new AugmentDataRejectedException("aramhex-data 파일을 받지 못했습니다: " + uri, exception);
        }
    }

    private <T> T parse(byte[] content, TypeReference<T> type) {
        T value = read(content, type);
        if (value == null) {
            throw new AugmentDataRejectedException("aramhex-data 파일 내용이 null입니다.");
        }
        return value;
    }

    private <T> T read(byte[] content, TypeReference<T> type) {
        try {
            return objectMapper.readValue(content, type);
        } catch (IOException exception) {
            throw new AugmentDataRejectedException("aramhex-data 파일을 읽지 못했습니다.", exception);
        }
    }
}
