package dfgg.infrastructure.external.client;

/**
 * Leaguepedia 로그인·조회 실패. 수집 배치는 이 예외가 나면 그 리그를 건너뛴다.
 */
public class LeaguepediaException extends RuntimeException {

    public LeaguepediaException(String message) {
        super(message);
    }
}
