package dfgg.evaluation;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.StringJoiner;

/**
 * 학습 데이터를 JSONL로 쓴다. 한 줄에 한 행이라 수천만 행도 스트리밍으로 읽고 쓸 수 있다 —
 * 전체를 메모리에 올리는 형식이면 2천만 행에서 감당이 안 된다.
 * <p>
 * NaN은 {@code null}로 내보낸다.
 * JSON에는 NaN 리터럴이 없고, 0으로 바꾸면 "데이터가 없다"와 "값이 0이다"가 파일에서 뒤섞인다
 * — 그 구분이 이번 작업의 실패 지표 분석의 전제라 형식 단계에서 잃으면 안 된다.
 * Python(pandas/numpy)은 null을 NaN으로 읽고, LightGBM은 NaN을 별도 분기로 다룬다.
 * <p>
 * Jackson을 쓰지 않고 직접 쓴다.
 * 행마다 객체를 만들면 수천만 번의 할당이 생기고, 형식이 단순해서 얻는 게 없다.
 */
public final class TrainingRowWriter implements AutoCloseable {

    private final BufferedWriter writer;

    public TrainingRowWriter(Writer writer) {
        this.writer = new BufferedWriter(writer, 1 << 20);
    }

    /** Python이 인덱스 대응을 검증할 때 쓰는 feature 이름 순서. */
    public static List<String> featureNamesHeader() {
        return FeatureName.exportNames();
    }

    public void write(TrainingRow row) {
        try {
            writer.write(toJson(row));
            writer.write('\n');
        } catch (IOException exception) {
            throw new UncheckedIOException("학습 데이터 기록 실패: qid=" + row.qid(), exception);
        }
    }

    private String toJson(TrainingRow row) {
        StringBuilder json = new StringBuilder(512);
        json.append('{')
                .append("\"qid\":\"").append(row.qid()).append("\",")
                .append("\"label\":").append(row.label()).append(',')
                .append("\"item_id\":").append(row.itemId()).append(',')
                .append("\"match_id\":\"").append(row.matchId()).append("\",")
                .append("\"patch\":\"").append(row.patch()).append("\",")
                .append("\"champion_id\":").append(row.championId()).append(',')
                .append("\"position\":\"").append(row.position()).append("\",")
                .append("\"purchase_step\":").append(row.purchaseStep()).append(',')
                .append("\"split_game\":\"").append(row.splitGame()).append("\",")
                .append("\"split_patch\":\"").append(row.splitPatch()).append("\",")
                .append("\"features\":").append(toJsonArray(row));
        return json.append('}').toString();
    }

    private String toJsonArray(TrainingRow row) {
        StringJoiner values = new StringJoiner(",", "[", "]");
        for (double value : row.vector().values()) {
            values.add(Double.isNaN(value) ? "null" : Double.toString(value));
        }
        return values.toString();
    }

    @Override
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("학습 데이터 파일을 닫지 못했습니다.", exception);
        }
    }
}
