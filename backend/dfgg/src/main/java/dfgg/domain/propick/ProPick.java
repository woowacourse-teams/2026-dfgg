package dfgg.domain.propick;

import dfgg.domain.champion.ChampionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대회 경기에서 선수 한 명의 픽. 주문·아이템·룬은 픽 행의 배열 컬럼이고 배열 위치가 칸 번호다(빈 칸은 null).
 */
@Entity
@Table(name = "pro_picks")
public class ProPick {

    private static final int SPELL_SLOTS = 2;
    private static final int ITEM_SLOTS = 7;
    private static final int RUNE_SLOTS = 9;

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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "spell_ids", nullable = false)
    private Integer[] spellIds = new Integer[SPELL_SLOTS];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "item_ids", nullable = false)
    private Long[] itemIds = new Long[ITEM_SLOTS];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "rune_ids", nullable = false)
    private Integer[] runeIds = new Integer[RUNE_SLOTS];

    protected ProPick() {
    }

    public ProPick(ProTournament tournament, ProPlayer player, String sourceGameId) {
        this.tournament = tournament;
        this.player = player;
        this.sourceGameId = sourceGameId;
    }

    /** 다시 수집하면 경기 정보와 주문·아이템·룬 배열을 통째로 덮어쓴다. */
    public void update(ProTournament tournament, OffsetDateTime playedAt, String team, ProPickSide side,
                       ChampionPosition position, Long championId, boolean win,
                       List<ProPickSpell> spells, List<ProPickItem> items, List<ProPickRune> runes) {
        this.tournament = tournament;
        this.playedAt = playedAt;
        this.team = team;
        this.side = side;
        this.position = position;
        this.championId = championId;
        this.win = win;
        this.spellIds = toSlots(spells, new Integer[SPELL_SLOTS], ProPickSpell::getSlot, ProPickSpell::getSpellId);
        this.itemIds = toSlots(items, new Long[ITEM_SLOTS], ProPickItem::getSlot, ProPickItem::getItemId);
        this.runeIds = toSlots(runes, new Integer[RUNE_SLOTS], ProPickRune::getSlot, ProPickRune::getRuneId);
    }

    private static <T, V> V[] toSlots(List<T> values, V[] slots, ToIntFunction<T> slotOf, Function<T, V> idOf) {
        values.forEach(value -> slots[slotOf.applyAsInt(value)] = idOf.apply(value));
        return slots;
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

    /** 채워진 칸만 칸 순서로 돌려준다. */
    public List<ProPickSpell> getSpells() {
        return filledSlots(spellIds, ProPickSpell::new);
    }

    public List<ProPickItem> getItems() {
        return filledSlots(itemIds, ProPickItem::new);
    }

    public List<ProPickRune> getRunes() {
        return filledSlots(runeIds, ProPickRune::new);
    }

    private static <V, R> List<R> filledSlots(V[] slots, BiFunction<Integer, V, R> slotValue) {
        return IntStream.range(0, slots.length)
                .filter(slot -> slots[slot] != null)
                .mapToObj(slot -> slotValue.apply(slot, slots[slot]))
                .toList();
    }
}
