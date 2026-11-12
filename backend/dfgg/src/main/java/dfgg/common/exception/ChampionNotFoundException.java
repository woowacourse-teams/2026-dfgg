package dfgg.common.exception;

/**
 * 요청에 적힌 챔피언을 찾을 수 없다.
 */
public class ChampionNotFoundException extends RuntimeException {
    public ChampionNotFoundException(String name) {
        super("챔피언을 찾을 수 없습니다: " + name);
    }
}
