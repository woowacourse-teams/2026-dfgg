package dfgg.application.recommend.v3.explanation;

import dfgg.domain.champion.ChampionTag;
import java.util.Set;

/**
 * 추천 이유에 쓰는 챔피언 한 명의 정보.
 *
 * @param name 한글 이름. 문장에 그대로 들어간다
 * @param tags 중복 제거된 태그. 없을 수도 있고, 그때는 태그로 무엇을 말하면 안 된다
 */
public record ChampionProfile(long championId, String name, Set<ChampionTag> tags) {

    public ChampionProfile {
        tags = Set.copyOf(tags);
    }
}
