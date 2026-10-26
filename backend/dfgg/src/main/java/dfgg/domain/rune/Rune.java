package dfgg.domain.rune;

import dfgg.domain.language.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 룬 카탈로그 항목. 이름만 저장하고 이미지는 S3에 따로 둔다. */
@Entity
@Table(name = "runes")
public class Rune {

    @Id
    @Column(name = "rune_id")
    private Integer runeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private RuneKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> name;

    /** 속한 계열(STYLE)의 id. 계열은 자기 id이고, 능력치 파편은 계열이 없어 null이다. */
    @Column(name = "tree_id")
    private Integer treeId;

    protected Rune() {
    }

    public Rune(int runeId, RuneKind kind, Map<String, String> name) {
        this(runeId, kind, name, null);
    }

    public Rune(int runeId, RuneKind kind, Map<String, String> name, Integer treeId) {
        this.runeId = runeId;
        this.kind = kind;
        this.name = Map.copyOf(name);
        this.treeId = treeId;
    }

    public int runeId() {
        return runeId;
    }

    public RuneKind kind() {
        return kind;
    }

    public String nameIn(Language language) {
        return language.pick(name);
    }

    public Integer treeId() {
        return treeId;
    }

    public boolean isStatShard() {
        return kind == RuneKind.STAT_SHARD;
    }
}
