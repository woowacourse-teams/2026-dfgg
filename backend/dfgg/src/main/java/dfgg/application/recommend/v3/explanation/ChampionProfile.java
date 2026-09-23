package dfgg.application.recommend.v3.explanation;

/**
 * 추천 이유에 쓰는 챔피언 한 명의 정보.
 *
 * @param riotKey 영문 키. 이미지가 이 키로 올라가 있다
 * @param name    한글 이름. 응답에 그대로 나간다
 */
public record ChampionProfile(
        long championId,
        String riotKey,
        String name
) {
}
