package dfgg.domain.itemstats;

import dfgg.domain.champion.ChampionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 패치·포지션별 아이템 픽률/승률. <b>patch를 키로 쓰는 유일한 통계 테이블이다.</b>
 *
 * <p>다른 테이블에서 patch를 뺀 것은 확률의 분모가 무너지기 때문인데, 여기는 분모가 아니라
 * feature다. 아이템 단위라 표본이 두텁고(패치당 참가자 수천~수만), 버프/너프는 인접 패치의
 * 픽률 차이로 드러난다 — 실측에서 오만은 0.14%에서 7.77%로 53배 움직였다.
 */
@Entity
@Table(
        name = "item_meta_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_item_meta_stats",
                columnNames = {"patch", "position", "item_id"}
        ),
        indexes = @Index(name = "idx_ims_position_item", columnList = "position, item_id")
)
public class ItemMetaStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "pick_count", nullable = false)
    private int pickCount;

    @Column(name = "win_count", nullable = false)
    private int winCount;

    /** 픽률의 분모. 그 패치·포지션의 (구매 순서가 완전한) 참가자 수. */
    @Column(name = "scope_game_count", nullable = false)
    private int scopeGameCount;

    protected ItemMetaStats() {
    }

    public Long getId() {
        return id;
    }

    public String getPatch() {
        return patch;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getPickCount() {
        return pickCount;
    }

    public int getWinCount() {
        return winCount;
    }

    public int getScopeGameCount() {
        return scopeGameCount;
    }
}
