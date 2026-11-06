package dfgg.evaluation;

import dfgg.application.recommend.v3.feature.FeatureVector;

/**
 * 학습 데이터 한 행 = (query, 후보 아이템) 한 쌍.
 *
 * @param qid        같은 query에 속한 행들을 묶는 키. LightGBM lambdarank의 group 단위다
 * @param label      등급 관련도 3/2/1/0 ({@link RelevanceLabeler})
 * @param splitGame  {@code match_id} 해시 기준 train/test — 같은 게임의 스냅샷이 흩어지지 않게
 * @param splitPatch 패치 기준 train/test — 최신 패치 일반화를 따로 본다
 */
public record TrainingRow(
        String qid,
        int label,
        Long itemId,
        FeatureVector vector,
        String matchId,
        String patch,
        Integer championId,
        String position,
        int purchaseStep,
        String splitGame,
        String splitPatch
) {
}
