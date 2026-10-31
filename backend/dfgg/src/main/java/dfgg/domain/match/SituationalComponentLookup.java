package dfgg.domain.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 적 챔피언별 구매 증가(lift)와 내 챔피언의 기본 구매율을 보관하는 패치별 조회표. */
public final class SituationalComponentLookup {
    private static final List<String> ROLES = List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT");

    private final String patch;
    private final Map<LiftKey, Lift> lifts;
    private final Map<BaseKey, Double> baseRates;

    public SituationalComponentLookup(String patch, Map<LiftKey, Lift> lifts, Map<BaseKey, Double> baseRates) {
        if (patch == null || !patch.matches("\\d+\\.\\d+")) throw new IllegalArgumentException("Situational 조회표 패치가 필요합니다.");
        this.patch = patch;
        this.lifts = Map.copyOf(lifts);
        this.baseRates = Map.copyOf(baseRates);
    }

    public String patch() {
        return patch;
    }

    /**
     * 기준을 넘는 아이템을 효과 → 최대 lift 내림차순 → 기본 구매율 내림차순으로 반환한다.
     * 보유 여부와 구매 가능 여부는 호출자가 판단한다.
     */
    public List<SituationalEvidence> evidenceFor(int championId, String role, Collection<Integer> enemyChampionIds,
                                                 SituationalThresholds thresholds) {
        if (!ROLES.contains(role)) return List.of();
        var evidence = new ArrayList<SituationalEvidence>();
        for (int item : SituationalComponentPolicy.items()) {
            Double baseRate = baseRates.get(new BaseKey(championId, role, item));
            if (baseRate == null || baseRate < thresholds.minBaseRate()) continue;
            var reasons = enemyChampionIds.stream().distinct()
                    .filter(enemy -> enemy > 0 && lifts.containsKey(new LiftKey(item, enemy, role)))
                    .map(enemy -> Map.entry(enemy, lifts.get(new LiftKey(item, enemy, role))))
                    .filter(entry -> entry.getValue().lift() >= thresholds.minLift())
                    .sorted(Comparator.comparingDouble((Map.Entry<Integer, Lift> entry) -> entry.getValue().lift()).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .toList();
            if (reasons.isEmpty()) continue;
            evidence.add(new SituationalEvidence(item, SituationalComponentPolicy.effectOf(item),
                    reasons.stream().map(Map.Entry::getKey).toList(), reasons.getFirst().getValue().lift(), baseRate));
        }
        evidence.sort(Comparator.comparing(SituationalEvidence::effect)
                .thenComparing(Comparator.comparingDouble(SituationalEvidence::maxLift).reversed())
                .thenComparing(Comparator.comparingDouble(SituationalEvidence::baseRate).reversed())
                .thenComparingInt(SituationalEvidence::itemId));
        return List.copyOf(evidence);
    }

    public record SituationalEvidence(int itemId, SituationalComponentPolicy.Effect effect, List<Integer> reasonChampionIds,
                                      double maxLift, double baseRate) {
        public SituationalEvidence {
            reasonChampionIds = List.copyOf(reasonChampionIds);
        }
    }

    public record LiftKey(int itemId, int enemyChampionId, String role) {
        public LiftKey {
            if (!SituationalComponentPolicy.items().contains(itemId) || enemyChampionId <= 0 || !ROLES.contains(role)) {
                throw new IllegalArgumentException("Situational 조회표 키가 유효하지 않습니다.");
            }
        }
    }

    public record BaseKey(int championId, String role, int itemId) {
        public BaseKey {
            if (championId <= 0 || !ROLES.contains(role) || !SituationalComponentPolicy.items().contains(itemId)) {
                throw new IllegalArgumentException("Situational 조회표 키가 유효하지 않습니다.");
            }
        }
    }

    public record Lift(int support, double lift) {
        public Lift {
            if (support < SituationalComponentPolicy.MIN_SUPPORT || !(lift >= SituationalComponentPolicy.LIFT_FLOOR)
                    || !Double.isFinite(lift)) {
                throw new IllegalArgumentException("Situational lift 하한 불충족");
            }
        }
    }
}
