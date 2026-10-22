package dfgg.domain.image;

/**
 * S3에 올려 둔 챔피언·아이템 이미지의 공개 URL을 만든다.
 * <p>
 * 주소는 업로드와 같은 버킷·리전에서, 경로는 업로드와 같은 {@link ImageKeys}에서 나온다 — 둘이 어긋날 수 없다.
 */
public record ImageUrls(
        String bucket,
        String region,
        String version
) {
    public ImageUrls {
        bucket = bucket.strip();
        region = region.strip();
        version = version.strip();
    }

    /**
     * 챔피언 이미지는 숫자 id가 아니라 영문 키(예: {@code MonkeyKing})로 올라가 있다.
     */
    public String championOf(String riotKey) {
        return origin() + ImageKeys.champion(version, riotKey);
    }

    public String itemOf(long itemId) {
        return origin() + ImageKeys.item(version, itemId);
    }

    /**
     * 룬은 설정 버전({@code version})이 아니라 추천 통계를 계산한 패치 폴더를 가리킨다.
     * 그래야 추천 내용과 이미지가 같은 패치를 본다.
     */
    public String runeOf(String patch, int runeId) {
        return origin() + ImageKeys.rune(patch, runeId);
    }

    private String origin() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }
}
