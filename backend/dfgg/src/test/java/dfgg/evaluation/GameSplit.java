package dfgg.evaluation;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 매치 ID로 train/test를 가르는 결정적 분할.
 * <p>
 * 스냅샷이 아니라 매치 단위로 가르는 것이 요점이다.
 * 같은 게임에서 나온 1코어 시점과 4코어 시점이 train/test에 나뉘어 들어가면,
 * 모델은 그 게임의 빌드를 이미 본 상태로 평가받는다.
 * 지표가 실제보다 좋게 나오고, 그 착시는 배포 후에야 드러난다.
 * <p>
 * 해시를 쓰는 이유는 재현성이다.
 * 무작위 셔플은 실행마다 분할이 달라져 평가 결과를 비교할 수 없고,
 * 수집 순서로 자르면 최신 패치가 통째로 test로 몰린다(그건 patch split에서 따로 한다).
 */
public final class GameSplit {

    private static final long HASH_BUCKETS = 10_000L;

    private final double trainRatio;

    public GameSplit(double trainRatio) {
        if (trainRatio <= 0.0 || trainRatio >= 1.0) {
            throw new IllegalArgumentException("train 비율은 0과 1 사이여야 합니다: " + trainRatio);
        }
        this.trainRatio = trainRatio;
    }

    public boolean isTrain(String matchId) {
        return bucketOf(matchId) < trainRatio * HASH_BUCKETS;
    }

    private long bucketOf(String matchId) {
        CRC32 crc32 = new CRC32();
        crc32.update(matchId.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue() % HASH_BUCKETS;
    }
}
