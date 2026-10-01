package dfgg.application.recommend.v3.feature;

/**
 * 현재 스키마의 지문. {@link FeatureName}을 바꾸면 이 상수도 함께 고쳐야 테스트가 통과한다.
 * <p>
 * 일부러 자동 계산하지 않는다. 손으로 고치는 그 행위가 "모델을 다시 학습해야 한다"는 확인 절차다
 * — feature 하나를 무심코 끼워 넣으면 기존 모델이 인덱스가 밀린 채 조용히 다른 값을 읽는데, 그건 지표로도 잘 드러나지 않는다.
 */
public final class FeatureSchemaFingerprint {

    public static final String EXPECTED = "84a57f48a9e3d5f2";

    private FeatureSchemaFingerprint() {
    }
}
