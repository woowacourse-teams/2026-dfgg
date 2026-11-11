package dfgg.domain.aram;

import java.util.Map;
import java.util.Optional;

/** 주문 계산식의 요소 하나. 기본값에 더할 범위를 답하고, 풀지 못하면 비어 있다. */
public sealed interface CalculationPart {

    Optional<CalculationRange> rangeWith(Map<String, Double> dataValues);

    record Constant(double value) implements CalculationPart {

        @Override
        public Optional<CalculationRange> rangeWith(Map<String, Double> dataValues) {
            return Optional.of(CalculationRange.fixed(value));
        }
    }

    record DataValue(String name) implements CalculationPart {

        @Override
        public Optional<CalculationRange> rangeWith(Map<String, Double> dataValues) {
            return Optional.ofNullable(dataValues.get(name)).map(CalculationRange::fixed);
        }
    }

    /** 챔피언 레벨에 따라 {@code start}에서 {@code end}까지 바뀐다. 레벨 보간과 레벨 구간 모두 이 범위로 쓴다. */
    record LevelRange(double start, double end) implements CalculationPart {

        @Override
        public Optional<CalculationRange> rangeWith(Map<String, Double> dataValues) {
            return Optional.of(CalculationRange.byLevel(start, end));
        }
    }

    /** 표기 규칙이 없는 요소. 틀린 숫자를 보여 주지 않으려고 계산식 전체를 풀지 않는다. */
    record Unsupported(String type) implements CalculationPart {

        @Override
        public Optional<CalculationRange> rangeWith(Map<String, Double> dataValues) {
            return Optional.empty();
        }
    }
}
