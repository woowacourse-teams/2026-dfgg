package dfgg.common.exception;

/**
 * 지원하지 않는 게임 언어 값을 받았다.
 */
public class InvalidLanguageException extends RuntimeException {
    public InvalidLanguageException(String lang) {
        super("지원하지 않는 언어입니다: " + lang);
    }
}
