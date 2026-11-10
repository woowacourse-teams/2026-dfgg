package dfgg.domain.image;

import java.util.function.Supplier;

/**
 * S3에 올려 둔 챔피언·아이템 이미지의 공개 URL을 만든다.
 * <p>
 * 주소는 업로드와 같은 버킷·리전에서, 경로는 업로드와 같은 {@link ImageKeys}에서 나온다 — 둘이 어긋날 수 없다.
 * 버전 폴더는 URL을 만들 때마다 읽는다. 서비스 패치가 실행 중에 바뀌면 다음 응답부터 새 폴더를 가리킨다.
 */
public final class ImageUrls {

    private final String bucket;
    private final String region;
    private final Supplier<String> version;

    public ImageUrls(String bucket, String region, Supplier<String> version) {
        this.bucket = bucket.strip();
        this.region = region.strip();
        this.version = version;
    }

    public ImageUrls(String bucket, String region, String version) {
        this(bucket, region, fixed(version.strip()));
    }

    private static Supplier<String> fixed(String version) {
        return () -> version;
    }

    public String version() {
        return version.get().strip();
    }

    /**
     * 챔피언 이미지는 숫자 id가 아니라 영문 키(예: {@code MonkeyKing})로 올라가 있다.
     */
    public String championOf(String riotKey) {
        return origin() + ImageKeys.champion(version(), riotKey);
    }

    public String itemOf(long itemId) {
        return origin() + ImageKeys.item(version(), itemId);
    }

    /**
     * 룬은 설정 버전({@code version})이 아니라 추천 통계를 계산한 패치 폴더를 가리킨다.
     * 그래야 추천 내용과 이미지가 같은 패치를 본다.
     */
    public String runeOf(String patch, int runeId) {
        return origin() + ImageKeys.rune(patch, runeId);
    }

    public String augmentOf(String patch, int augmentId) {
        return origin() + ImageKeys.augment(patch, augmentId);
    }

    public String summonerSpellOf(String riotKey) {
        return origin() + ImageKeys.summonerSpell(version(), riotKey);
    }

    private String origin() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }
}
