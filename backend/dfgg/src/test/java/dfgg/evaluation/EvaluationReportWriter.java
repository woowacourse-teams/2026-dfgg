package dfgg.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 평가 리포트를 파일로 남긴다. 실패해도 예외를 던지지 않는다.
 * <p>
 * export는 오래 걸린다. 그 뒤에 붙은 마크다운 리포트 한 줄이 실패해서 작업 전체가 FAILED로 끝난 적이 있다
 * — 데이터는 이미 온전히 저장된 뒤였고, 원인은 새 환경에 디렉터리가 없었던 것뿐이었다.
 * <p>
 * 리포트는 부수적인 산출물이다. 본 작업의 성패를 좌우해서는 안 된다.
 * 다만 조용히 삼키면 리포트가 없는 이유를 알 수 없으므로, 표준 에러에 남기고 성공 여부를 돌려준다.
 */
public class EvaluationReportWriter {

    /** @return 저장에 성공했으면 true */
    public boolean write(Path path, String report) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, report);
            return true;
        } catch (IOException exception) {
            System.err.printf(
                    "리포트를 저장하지 못했습니다 (export 자체는 완료됨): path=%s, 원인=%s%n",
                    path, exception.getMessage());
            return false;
        }
    }
}
