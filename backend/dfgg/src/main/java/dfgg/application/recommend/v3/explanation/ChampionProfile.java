package dfgg.application.recommend.v3.explanation;

/**
 * 추천 이유에 쓰는 챔피언 한 명의 정보.
 *
 * @param name 한글 이름. 응답에 그대로 나간다
 */
public record ChampionProfile(long championId, String name) {
}
