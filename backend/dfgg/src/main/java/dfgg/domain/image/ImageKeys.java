package dfgg.domain.image;

/**
 * S3 이미지 키 규칙. 업로드와 응답 URL이 이 한 곳을 쓴다.
 * <p>
 * 둘이 따로 규칙을 들면 한쪽만 바뀌었을 때 응답 URL이 없는 객체를 가리켜 403이 나고, 서버는 알아채지 못한다.
 * 챔피언은 영문 키(예: {@code MonkeyKing}), 아이템은 아이템 id로 저장한다 — 응답을 만들 때 가진 값이 그것뿐이다.
 */
public final class ImageKeys {

    private static final String ROOT = "dfgg/images";

    private ImageKeys() {
    }

    public static String champion(String version, String riotKey) {
        return ROOT + "/" + version + "/champions/" + riotKey + ".png";
    }

    public static String item(String version, long itemId) {
        return ROOT + "/" + version + "/items/" + itemId + ".png";
    }
}
