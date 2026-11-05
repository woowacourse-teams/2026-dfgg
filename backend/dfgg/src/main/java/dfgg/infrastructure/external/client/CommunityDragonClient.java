package dfgg.infrastructure.external.client;

import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.infrastructure.external.dto.CherryAugment;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CommunityDragon에서 패치별 증강 이름, 등급, 아이콘을 받는다.
 * <p>
 * {@code latest}가 아니라 패치 폴더를 쓴다. 픽률 데이터의 패치는 라이브보다 늦을 수 있어서 그 패치의 이름과 아이콘을 맞춘다.
 */
@Component
public class CommunityDragonClient {

    private static final String BASE_URL = "https://raw.communitydragon.org";
    private static final String GAME_DATA = "/{patch}/plugins/rcp-be-lol-game-data/global/";
    private static final Pattern ICON_PATH = Pattern.compile(
            "/lol-game-data/assets/(ASSETS(?:/[A-Za-z0-9_-]+)+\\.png)");
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private final RestClient restClient;

    public CommunityDragonClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    public CommunityDragonAugments getAugments(String patch) {
        return CommunityDragonAugments.of(fetchAugments(patch, "ko_kr"), fetchAugments(patch, "default"));
    }

    private List<CherryAugment> fetchAugments(String patch, String locale) {
        String file = locale + "/v1/cherry-augments.json";
        CherryAugment[] augments = fetch(file, GAME_DATA + "{locale}/v1/cherry-augments.json", CherryAugment[].class,
                patch, locale);
        if (augments == null || Arrays.stream(augments).anyMatch(Objects::isNull)) {
            throw new AugmentDataRejectedException("CommunityDragon 증강 목록이 비정상입니다: " + locale);
        }
        return List.of(augments);
    }

    /** 경로는 외부 값이고 주소에 그대로 붙으므로 게임 자산 경로만 받는다. 파일 주소는 소문자다. */
    public byte[] getAugmentIcon(String patch, String iconPath) {
        byte[] image = fetch(iconPath, GAME_DATA + "default/" + assetPathOf(iconPath), byte[].class, patch);
        if (image == null || !startsWithPngSignature(image)) {
            throw new AugmentDataRejectedException("증강 아이콘이 PNG 형식이 아닙니다: " + iconPath);
        }
        return image;
    }

    private String assetPathOf(String iconPath) {
        Matcher matcher = ICON_PATH.matcher(String.valueOf(iconPath));
        if (!matcher.matches()) {
            throw new AugmentDataRejectedException("증강 아이콘 경로가 올바르지 않습니다: " + iconPath);
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private boolean startsWithPngSignature(byte[] image) {
        return image.length >= PNG_SIGNATURE.length
                && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(image, PNG_SIGNATURE.length));
    }

    private <T> T fetch(String file, String uri, Class<T> type, Object... variables) {
        try {
            return restClient.get()
                    .uri(uri, variables)
                    .retrieve()
                    .body(type);
        } catch (RestClientException exception) {
            throw new AugmentDataRejectedException("CommunityDragon 파일을 받지 못했습니다: " + file, exception);
        }
    }
}
