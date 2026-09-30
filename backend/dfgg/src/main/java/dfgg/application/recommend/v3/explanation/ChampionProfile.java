package dfgg.application.recommend.v3.explanation;

import java.util.Map;

/**
 * 추천 이유에 쓰는 챔피언 한 명의 정보.
 *
 * @param riotKey 영문 키. 이미지가 이 키로 올라가 있다
 * @param name    언어별 이름. 응답을 만들 때 요청한 언어를 고른다
 */
public record ChampionProfile(
        long championId,
        String riotKey,
        Map<String, String> name
) {
}
