package dfgg.domain.aram;

import dfgg.domain.language.Language;
import java.util.Arrays;
import java.util.Optional;

/**
 * 계산식 능력치 계수가 가리키는 챔피언 능력치({@code mStat}).
 * <p>
 * 번호는 16.22 챔피언 데이터와 대조해 확인한 것만 둔다. 예: 초가스 R(추가 체력) 12, 갈리오 패시브(추가 마법 저항력) 6,
 * 말파이트 E(방어력) 1, 마스터 이 패시브(공격력) 2, 번호가 없는 주문력 계수 0. 표에 없는 번호는 설명을 만들지 않는다.
 */
public enum ChampionStat {

    ABILITY_POWER(0, "주문력", "AP"),
    ARMOR(1, "방어력", "Armor"),
    ATTACK_DAMAGE(2, "공격력", "AD"),
    MAGIC_RESIST(6, "마법 저항력", "Magic Resist"),
    HEALTH(12, "체력", "Health");

    private final int code;
    private final String koreanName;
    private final String englishName;

    ChampionStat(int code, String koreanName, String englishName) {
        this.code = code;
        this.koreanName = koreanName;
        this.englishName = englishName;
    }

    public static Optional<ChampionStat> of(int code) {
        return Arrays.stream(values())
                .filter(stat -> stat.code == code)
                .findFirst();
    }

    String nameIn(Language language) {
        if (language == Language.KO_KR) {
            return koreanName;
        }
        return englishName;
    }
}
