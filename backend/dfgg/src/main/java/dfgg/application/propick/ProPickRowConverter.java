package dfgg.application.propick;

import dfgg.domain.catalog.EnglishNameIndex;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.propick.ProPickItem;
import dfgg.domain.propick.ProPickRune;
import dfgg.domain.propick.ProPickSide;
import dfgg.domain.propick.ProPickSpell;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Leaguepedia {@code ScoreboardPlayers} 한 행을 픽으로 바꾼다 (스펙 §4.3 매핑 표).
 * 챔피언·포지션·진영·시각을 못 바꾸면 행을 건너뛰고, 주문·아이템·룬은 못 바꾼 칸만 뺀다.
 */
@Component
public class ProPickRowConverter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, ProPickSide> SIDES = Map.of("1", ProPickSide.BLUE, "2", ProPickSide.RED);
    private static final Map<String, ChampionPosition> ROLES = Map.of(
            "Top", ChampionPosition.TOP,
            "Jungle", ChampionPosition.JUNGLE,
            "Mid", ChampionPosition.MID,
            "Bot", ChampionPosition.BOTTOM,
            "Support", ChampionPosition.SUPPORT);
    private static final int INVENTORY_SLOTS = 6;
    private static final int TRINKET_SLOT = 6;
    private static final int FLAT_HEALTH_SLOT = 8;
    private static final String HEALTH = "health";
    private static final String HEALTH_SCALING = "Health Scaling";

    public ProPickConversion convert(Map<String, String> row, ProPickCatalog catalog) {
        Optional<OffsetDateTime> playedAt = playedAtOf(field(row, "DateTime UTC"));
        if (playedAt.isEmpty()) {
            return ProPickConversion.skipped(ProPickSkipReason.INVALID_DATE_TIME);
        }
        ProPickSide side = SIDES.get(field(row, "Side"));
        if (side == null) {
            return ProPickConversion.skipped(ProPickSkipReason.UNKNOWN_SIDE);
        }
        ChampionPosition position = ROLES.get(field(row, "Role"));
        if (position == null) {
            return ProPickConversion.skipped(ProPickSkipReason.UNKNOWN_ROLE);
        }
        Optional<Integer> championId = idOf(catalog.champions(), field(row, "Champion"));
        if (championId.isEmpty()) {
            return ProPickConversion.skipped(ProPickSkipReason.UNKNOWN_CHAMPION);
        }
        return ProPickConversion.converted(new ProPickDraft(
                field(row, "GameId"),
                field(row, "OverviewPage"),
                field(row, "Link"),
                field(row, "Team"),
                playedAt.get(),
                side,
                position,
                championId.get(),
                "Yes".equals(field(row, "PlayerWin")),
                spellsOf(field(row, "SummonerSpells"), catalog.spells()),
                itemsOf(field(row, "Items"), field(row, "Trinket"), catalog.items()),
                runesOf(field(row, "Runes"), catalog.runes())));
    }

    private Optional<OffsetDateTime> playedAtOf(String dateTime) {
        try {
            return Optional.of(LocalDateTime.parse(dateTime, DATE_TIME).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private List<ProPickSpell> spellsOf(String spells, EnglishNameIndex index) {
        List<ProPickSpell> result = new ArrayList<>();
        List<String> names = split(spells, ",");
        for (int slot = 0; slot < names.size(); slot++) {
            int spellSlot = slot;
            idOf(index, names.get(slot)).ifPresent(id -> result.add(new ProPickSpell(spellSlot, id)));
        }
        return result;
    }

    // 인벤토리는 ; 위치가 칸이다. 빈칸은 행을 만들지 않는다.
    private List<ProPickItem> itemsOf(String items, String trinket, EnglishNameIndex index) {
        List<ProPickItem> result = new ArrayList<>();
        List<String> names = split(items, ";");
        for (int slot = 0; slot < Math.min(names.size(), INVENTORY_SLOTS); slot++) {
            int itemSlot = slot;
            idOf(index, names.get(slot)).ifPresent(id -> result.add(new ProPickItem(itemSlot, id.longValue())));
        }
        idOf(index, trinket).ifPresent(id -> result.add(new ProPickItem(TRINKET_SLOT, id.longValue())));
        return result;
    }

    private List<ProPickRune> runesOf(String runes, EnglishNameIndex index) {
        List<ProPickRune> result = new ArrayList<>();
        List<String> names = split(runes, ",");
        for (int slot = 0; slot < names.size(); slot++) {
            int runeSlot = slot;
            idOf(index, runeNameOf(names.get(slot), slot))
                    .ifPresent(id -> result.add(new ProPickRune(runeSlot, id)));
        }
        return result;
    }

    // Leaguepedia는 두 체력 파편을 모두 Health로 적는다. 세 번째 파편 칸이면 고정 체력, 그 밖이면 레벨당 체력이다.
    private String runeNameOf(String name, int slot) {
        if (HEALTH.equalsIgnoreCase(name.strip()) && slot != FLAT_HEALTH_SLOT) {
            return HEALTH_SCALING;
        }
        return name;
    }

    private Optional<Integer> idOf(EnglishNameIndex index, String name) {
        return index.idOf(name);
    }

    private List<String> split(String value, String separator) {
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(separator, -1));
    }

    // Cargo는 값을 HTML 엔티티로 준다(&#039;). ;로 칸을 나누기 전에 풀어야 엔티티의 ;가 칸을 쪼개지 않는다.
    private String field(Map<String, String> row, String name) {
        return HtmlUtils.htmlUnescape(row.getOrDefault(name, ""));
    }
}
