package dfgg.application.recommend.v3;

import java.util.List;
import java.util.Objects;

/**
 * 추천을 받는 시점에 10명이 어떤 상태인가. 상황을 보내지 않는 요청이 있어 "모름"이 실제로 있는 상태다.
 *
 * <p>아군·상대가 비어 있는 {@link Known}은 "아는데 선수가 없다"이고 {@link Unknown}과 다르다.
 */
public sealed interface GameSituation {

    static GameSituation unknown() {
        return new Unknown();
    }

    static Known of(MyState me, List<PlayerState> allies, List<PlayerState> enemies) {
        return new Known(me, allies, enemies);
    }

    boolean isUnknown();

    record Unknown() implements GameSituation {

        @Override
        public boolean isUnknown() {
            return true;
        }
    }

    /** {@code allies}는 나를 뺀 아군이다. */
    record Known(MyState me, List<PlayerState> allies, List<PlayerState> enemies) implements GameSituation {

        public Known {
            Objects.requireNonNull(me, "내 상태는 필수입니다.");
            allies = List.copyOf(allies);
            enemies = List.copyOf(enemies);
        }

        @Override
        public boolean isUnknown() {
            return false;
        }
    }
}
