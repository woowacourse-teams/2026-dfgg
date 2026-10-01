package dfgg.common;

public class CompositionStatsNotFoundException extends RuntimeException {

    public CompositionStatsNotFoundException(String champion, String position) {
        super(String.format("해당 챔피언(%s)의 포지션(%s)에 대한 조합 통계 데이터가 존재하지 않습니다.", champion, position));
    }
}
