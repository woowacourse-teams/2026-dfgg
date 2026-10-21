package dfgg.domain.rune;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;

/**
 * 한 경기 참가자의 룬 페이지. 룬 통계를 이 테이블만으로 집계하도록 챔피언·팀·포지션·승패를 함께 둔다.
 * 맞라인 상대는 같은 경기·포지션에서 팀이 다른 참가자다.
 */
@Entity
@Table(
        name = "normalized_match_participant_runes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participant_rune",
                columnNames = {"match_id", "participant_id"}
        ),
        indexes = @Index(
                name = "idx_participant_rune_champion_position_patch",
                columnList = "champion_id, position, patch"
        )
)
public class NormalizedMatchParticipantRune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, length = 32)
    private String matchId;

    @Column(name = "participant_id", nullable = false)
    private int participantId;

    @Column(name = "patch", nullable = false, length = 16)
    private String patch;

    @Column(name = "champion_id", nullable = false)
    private int championId;

    @Column(name = "team_id", nullable = false)
    private int teamId;

    @Column(name = "position", length = 32)
    private String position;

    @Column(name = "win", nullable = false)
    private boolean win;

    @Column(name = "primary_style_id", nullable = false)
    private int primaryStyleId;

    @Column(name = "keystone_id", nullable = false)
    private int keystoneId;

    @Column(name = "primary_perk1_id", nullable = false)
    private int primaryPerk1Id;

    @Column(name = "primary_perk2_id", nullable = false)
    private int primaryPerk2Id;

    @Column(name = "primary_perk3_id", nullable = false)
    private int primaryPerk3Id;

    @Column(name = "sub_style_id", nullable = false)
    private int subStyleId;

    @Column(name = "sub_perk1_id", nullable = false)
    private int subPerk1Id;

    @Column(name = "sub_perk2_id", nullable = false)
    private int subPerk2Id;

    @Column(name = "stat_offense_id", nullable = false)
    private int statOffenseId;

    @Column(name = "stat_flex_id", nullable = false)
    private int statFlexId;

    @Column(name = "stat_defense_id", nullable = false)
    private int statDefenseId;

    protected NormalizedMatchParticipantRune() {
    }

    public NormalizedMatchParticipantRune(
            String matchId,
            int participantId,
            String patch,
            int championId,
            int teamId,
            String position,
            boolean win,
            RunePage runePage
    ) {
        this.matchId = matchId;
        this.participantId = participantId;
        this.patch = patch;
        this.championId = championId;
        this.teamId = teamId;
        this.position = position;
        this.win = win;
        this.primaryStyleId = runePage.primaryStyleId();
        this.keystoneId = runePage.keystoneId();
        this.primaryPerk1Id = runePage.primaryPerkIds().get(1);
        this.primaryPerk2Id = runePage.primaryPerkIds().get(2);
        this.primaryPerk3Id = runePage.primaryPerkIds().get(3);
        this.subStyleId = runePage.subStyleId();
        this.subPerk1Id = runePage.subPerkIds().get(0);
        this.subPerk2Id = runePage.subPerkIds().get(1);
        this.statOffenseId = runePage.statShards().offenseId();
        this.statFlexId = runePage.statShards().flexId();
        this.statDefenseId = runePage.statShards().defenseId();
    }

    /** 칸에 펼쳐 저장한 룬을 게임 화면의 선택 순서대로 다시 묶는다. */
    public RunePage runePage() {
        return new RunePage(
                primaryStyleId,
                List.of(keystoneId, primaryPerk1Id, primaryPerk2Id, primaryPerk3Id),
                subStyleId,
                List.of(subPerk1Id, subPerk2Id),
                new StatShards(statOffenseId, statFlexId, statDefenseId)
        );
    }
}
