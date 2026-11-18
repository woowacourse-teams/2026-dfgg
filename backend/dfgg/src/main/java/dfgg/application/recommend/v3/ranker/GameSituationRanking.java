package dfgg.application.recommend.v3.ranker;

import java.util.Objects;

/**
 * 게임 상황까지 읽는 새 모델의 랭커 짝. 서버에 새 모델이 없을 수 있다.
 *
 * <p>새 모델의 랭커를 스프링 빈으로 등록하지 않고 이 객체에 담는다. 같은 타입의 빈이 둘이 되면
 * 타입만으로 랭커를 받는 곳이 어느 것을 받을지 정해지지 않기 때문이다.
 *
 * <p>새 모델의 짝을 그대로 꺼내는 메서드는 두지 않는다. 없을 때 무엇을 쓸지를 부르는 쪽이 반드시
 * 정하게 하려는 것이다.
 */
public final class GameSituationRanking {

    private final CoreRanking ranking;

    private GameSituationRanking(CoreRanking ranking) {
        this.ranking = ranking;
    }

    public static GameSituationRanking available(CoreRanking ranking) {
        return new GameSituationRanking(Objects.requireNonNull(ranking, "새 모델의 랭커 짝은 필수입니다."));
    }

    public static GameSituationRanking absent() {
        return new GameSituationRanking(null);
    }

    public boolean isAvailable() {
        return ranking != null;
    }

    /** 새 모델이 있으면 새 모델의 짝을, 없으면 넘겨받은 짝을 돌려준다. */
    public CoreRanking orElse(CoreRanking fallback) {
        if (isAvailable()) {
            return ranking;
        }
        return fallback;
    }
}
