package dfgg.application.recommend.v3.feature;

/**
 * 현재 스키마의 지문. {@link FeatureName}을 바꾸면 이 상수도 함께 고쳐야 테스트가 통과한다.
 * <p>
 * 일부러 자동 계산하지 않는다. 손으로 고치는 그 행위가 "모델을 다시 학습해야 한다"는 확인 절차다
 * — feature 하나를 무심코 끼워 넣으면 기존 모델이 인덱스가 밀린 채 조용히 다른 값을 읽는데, 그건 지표로도 잘 드러나지 않는다.
 */
public final class FeatureSchemaFingerprint {

    public static final String EXPECTED = "24b965cb9a191fc6";

    /**
     * 기준 계약({@link FeatureName#baselineExportNames()})의 지문. 지금 운영 모델이 이 계약으로 학습됐다.
     * <p>
     * {@link #EXPECTED}와 달리 feature를 뒤에 더해도 고치지 않는다.
     * 이 값이 바뀌어야 한다면 앞쪽 feature를 건드린 것이고, 운영 모델을 그대로 쓸 수 없다.
     */
    public static final String BASELINE = "84a57f48a9e3d5f2";

    /**
     * 상황 v1 계약({@link FeatureName#situationV1ExportNames()})의 지문. dev v5에 올라간 첫 게임 상황 모델이 이 계약으로 학습됐다.
     * <p>
     * {@link #BASELINE}처럼 feature를 뒤에 더해도 고치지 않는다.
     * 이 값이 바뀌어야 한다면 앞 70칸을 건드린 것이고, 그 모델을 그대로 쓸 수 없다.
     */
    public static final String SITUATION_V1 = "50714dac4aca299c";

    private FeatureSchemaFingerprint() {
    }
}
