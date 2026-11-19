package dfgg.common.exception;

public class ChampionPositionNotFoundException extends RuntimeException {

    public ChampionPositionNotFoundException(String champion) {
        super(String.format("해당 챔피언(%s)의 라인 기록이 없어 포지션을 정할 수 없습니다.", champion));
    }
}
