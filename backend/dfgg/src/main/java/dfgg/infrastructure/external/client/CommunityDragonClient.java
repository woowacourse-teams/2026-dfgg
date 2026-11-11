package dfgg.infrastructure.external.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.common.exception.AugmentDataRejectedException;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.dto.CherryAugment;
import dfgg.infrastructure.external.dto.CommunityDragonAugments;
import dfgg.infrastructure.external.dto.CommunityDragonDescriptions;
import dfgg.infrastructure.external.dto.GameStrings;
import dfgg.infrastructure.external.dto.KiwiAugments;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CommunityDragon에서 패치별 증강 이름, 등급, 아이콘, 요약 설명을 받는다.
 * <p>
 * {@code latest}가 아니라 패치 폴더를 쓴다. 픽률 데이터의 패치는 라이브보다 늦을 수 있어서 그 패치의 이름과 아이콘을 맞춘다.
 * 이름·등급·아이콘은 클라이언트용 {@code cherry-augments.json}에서, 설명은 그 파일의 설명 칸이 비어 있어 게임 파일에서 받는다.
 */
@Component
public class CommunityDragonClient {

    private static final String BASE_URL = "https://raw.communitydragon.org";
    private static final String GAME_DATA = "/{patch}/plugins/rcp-be-lol-game-data/global/";
    private static final Pattern ICON_PATH = Pattern.compile(
            "/lol-game-data/assets/(ASSETS(?:/[A-Za-z0-9_-]+)+\\.png)");
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final String GAME_FILES = "/{patch}/game/";
    private static final String KIWI_FILE = "maps/modespecificdata/kiwi.bin.json";
    /** 언어 폴더 아래 메뉴 폴더는 언어와 상관없이 {@code en_us}다. CommunityDragon이 푼 경로 그대로다. */
    private static final String STRING_FILE = "data/menu/en_us/lol.stringtable.json";
    private static final int MAX_STRING_ROUNDS = 5;
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /** 증강 정의({@code kiwi.bin.json})로 요약 설명 키와 수치를 찾고, 언어별 문자열 파일에서 그 문장을 찾는다. */
    public CommunityDragonDescriptions getAugmentDescriptions(String patch) {
        KiwiAugments augments = stream(KIWI_FILE, GAME_FILES + KIWI_FILE, KiwiAugments::read, patch);
        List<GameStrings> strings = Arrays.stream(Language.values())
                .map(language -> fetchStrings(patch, language, augments.descriptionKeys()))
                .toList();
        return new CommunityDragonDescriptions(augments, strings);
    }

    /**
     * 설명 문장이 끼워 넣는 문장은 파일의 어디에 있는지 미리 알 수 없다. 새로 필요해진 키만 들고 파일을 다시 받는다.
     * 16.22 기준 두 번이면 끝난다.
     */
    private GameStrings fetchStrings(String patch, Language language, Set<String> descriptionKeys) {
        String locale = language.gameLanguage().toLowerCase(Locale.ROOT);
        GameStrings strings = GameStrings.empty(language);
        Set<String> requested = new HashSet<>();
        Set<String> wanted = descriptionKeys;
        for (int round = 0; round < MAX_STRING_ROUNDS && !wanted.isEmpty(); round++) {
            requested.addAll(wanted);
            Set<String> keys = Set.copyOf(wanted);
            GameStrings found = stream(locale + "/" + STRING_FILE, GAME_FILES + "{locale}/" + STRING_FILE,
                    parser -> GameStrings.read(language, parser, keys), patch, locale);
            strings = strings.merge(found);
            wanted = found.includedKeys().stream()
                    .filter(key -> !requested.contains(key))
                    .collect(Collectors.toSet());
        }
        return strings;
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

    /** 큰 파일을 바이트로 다 받지 않고 응답 스트림에서 바로 읽는다. 읽기 실패도 받기 실패와 같이 거부한다. */
    private <T> T stream(String file, String uri, StreamReader<T> reader, Object... variables) {
        try {
            return restClient.get()
                    .uri(uri, variables)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new AugmentDataRejectedException(
                                    "CommunityDragon 파일을 받지 못했습니다: " + file + " " + response.getStatusCode());
                        }
                        try (JsonParser parser = JSON.createParser(response.getBody())) {
                            return reader.read(parser);
                        }
                    });
        } catch (RestClientException exception) {
            throw new AugmentDataRejectedException("CommunityDragon 파일을 읽지 못했습니다: " + file, exception);
        }
    }

    @FunctionalInterface
    private interface StreamReader<T> {

        T read(JsonParser parser) throws IOException;
    }
}
