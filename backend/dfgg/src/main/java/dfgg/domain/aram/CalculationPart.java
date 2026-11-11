package dfgg.domain.aram;

import dfgg.domain.aram.CalculationValue.StatTerm;
import java.util.Map;
import java.util.Optional;

/** 주문 계산식의 요소 하나. 계산식 값에 더할 몫을 답하고, 풀지 못하면 비어 있다. */
public sealed interface CalculationPart {

    Optional<CalculationValue> valueWith(Map<String, Double> dataValues);

    record Constant(double value) implements CalculationPart {

        @Override
        public Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
            return Optional.of(CalculationValue.fixed(value));
        }
    }

    record DataValue(String name) implements CalculationPart {

        @Override
        public Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
            return Optional.ofNullable(dataValues.get(name)).map(CalculationValue::fixed);
        }
    }

    /** 챔피언 레벨에 따라 {@code start}에서 {@code end}까지 바뀐다. 레벨 보간과 레벨 구간 모두 이 범위로 쓴다. */
    record LevelRange(double start, double end) implements CalculationPart {

        @Override
        public Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
            return Optional.of(CalculationValue.byLevel(start, end));
        }
    }

    /**
     * 챔피언 능력치에 비례하는 몫. 능력치를 알 수 없어 기본값에 더하지 않고 계수로 붙인다.
     *
     * @param bonus       추가 능력치({@code mStatFormula} 2)에 비례하는지
     * @param coefficient 계수. 숫자 하나여야 한다
     */
    record StatScaling(ChampionStat stat, boolean bonus, CalculationPart coefficient) implements CalculationPart {

        @Override
        public Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
            return coefficient.valueWith(dataValues)
                    .filter(CalculationValue::isConstant)
                    .map(value -> CalculationValue.scaling(new StatTerm(stat, bonus, value.low())));
        }
    }

    /** 표기 규칙이 없는 요소. 틀린 숫자를 보여 주지 않으려고 계산식 전체를 풀지 않는다. */
    record Unsupported(String type) implements CalculationPart {

        @Override
        public Optional<CalculationValue> valueWith(Map<String, Double> dataValues) {
            return Optional.empty();
        }
    }
}
