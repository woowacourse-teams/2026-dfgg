package dfgg.domain.spell;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소환사 주문 카탈로그 한 행. id는 Data Dragon {@code key}(예: 4 = 점멸)다.
 */
@Entity
@Table(name = "summoner_spells")
public class SummonerSpell {

    @Id
    @Column(name = "spell_id")
    private Integer spellId;

    @Column(name = "riot_key", nullable = false, unique = true)
    private String riotKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> name;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    protected SummonerSpell() {
    }

    public SummonerSpell(Integer spellId, String riotKey, Map<String, String> name, String iconKey) {
        this.spellId = spellId;
        this.riotKey = riotKey;
        this.name = Map.copyOf(name);
        this.iconKey = iconKey;
    }

    public Integer getSpellId() {
        return spellId;
    }

    public String getRiotKey() {
        return riotKey;
    }

    public Map<String, String> getName() {
        return Map.copyOf(name);
    }

    public String getIconKey() {
        return iconKey;
    }
}
