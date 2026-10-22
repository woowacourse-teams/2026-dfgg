package dfgg.domain.propick;

import dfgg.domain.champion.ChampionPosition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * 대회 경기에서 선수 한 명의 픽. 주문·아이템·룬은 칸 순서가 있는 자식 행이다.
 * 자식 컬렉션은 픽 묶음 단위로 한 번에 읽는다 ({@link BatchSize}) — 픽마다 따로 읽지 않는다.
 */
@Entity
@Table(name = "pro_picks")
public class ProPick {

    private static final int CHILD_BATCH_SIZE = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private ProTournament tournament;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private ProPlayer player;

    @Column(name = "source_game_id", nullable = false)
    private String sourceGameId;

    @Column(name = "played_at", nullable = false)
    private OffsetDateTime playedAt;

    @Column(nullable = false)
    private String team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ProPickSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChampionPosition position;

    @Column(name = "champion_id", nullable = false)
    private Long championId;

    @Column(nullable = false)
    private boolean win;

    @ElementCollection
    @CollectionTable(name = "pro_pick_spells", joinColumns = @JoinColumn(name = "pick_id"))
    @OrderBy("slot")
    @BatchSize(size = CHILD_BATCH_SIZE)
    private List<ProPickSpell> spells = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "pro_pick_items", joinColumns = @JoinColumn(name = "pick_id"))
    @OrderBy("slot")
    @BatchSize(size = CHILD_BATCH_SIZE)
    private List<ProPickItem> items = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "pro_pick_runes", joinColumns = @JoinColumn(name = "pick_id"))
    @OrderBy("slot")
    @BatchSize(size = CHILD_BATCH_SIZE)
    private List<ProPickRune> runes = new ArrayList<>();

    protected ProPick() {
    }

    public Long getId() {
        return id;
    }

    public ProTournament getTournament() {
        return tournament;
    }

    public ProPlayer getPlayer() {
        return player;
    }

    public String getSourceGameId() {
        return sourceGameId;
    }

    public OffsetDateTime getPlayedAt() {
        return playedAt;
    }

    public String getTeam() {
        return team;
    }

    public ProPickSide getSide() {
        return side;
    }

    public ChampionPosition getPosition() {
        return position;
    }

    public Long getChampionId() {
        return championId;
    }

    public boolean isWin() {
        return win;
    }

    public List<ProPickSpell> getSpells() {
        return List.copyOf(spells);
    }

    public List<ProPickItem> getItems() {
        return List.copyOf(items);
    }

    public List<ProPickRune> getRunes() {
        return List.copyOf(runes);
    }
}
