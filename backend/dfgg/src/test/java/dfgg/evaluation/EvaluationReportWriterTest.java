package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationReportWriterTest {

    private final EvaluationReportWriter writer = new EvaluationReportWriter();

    @Test
    @DisplayName("상위 디렉터리가 없으면 만들어서 쓴다 — 새 환경에는 tasks/가 없다")
    void write_WhenParentDirectoryIsMissing_CreatesItAndWrites() throws IOException {
        Path target = Path.of(System.getProperty("java.io.tmpdir"),
                "dfgg-report-test-" + System.nanoTime(), "nested", "report.md");

        writer.write(target, "# 리포트");

        assertThat(Files.readString(target)).isEqualTo("# 리포트");
        Files.deleteIfExists(target);
        Files.deleteIfExists(target.getParent());
        Files.deleteIfExists(target.getParent().getParent());
    }

    @Test
    @DisplayName("이미 있는 파일은 덮어쓴다 — 매 실행이 최신 결과를 남긴다")
    void write_WhenFileExists_Overwrites(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("report.md");
        Files.writeString(target, "낡은 내용");

        writer.write(target, "새 내용");

        assertThat(Files.readString(target)).isEqualTo("새 내용");
    }

    @Test
    @DisplayName("쓸 수 없어도 예외를 던지지 않는다 — 97분짜리 export를 리포트가 무효로 만들면 안 된다")
    void write_WhenPathIsUnwritable_DoesNotThrow(@TempDir Path directory) throws IOException {
        // 상위 경로 자리에 파일이 있으면 디렉터리를 만들 수 없다.
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "나는 디렉터리가 아니다");
        Path target = blocker.resolve("report.md");

        assertThatCode(() -> writer.write(target, "# 리포트")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실패했는지 여부를 돌려준다 — 조용히 삼키면 리포트가 없는 이유를 모른다")
    void write_ReportsWhetherItSucceeded(@TempDir Path directory) throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "나는 디렉터리가 아니다");

        assertThat(writer.write(directory.resolve("report.md"), "# 리포트")).isTrue();
        assertThat(writer.write(blocker.resolve("report.md"), "# 리포트")).isFalse();
    }
}
