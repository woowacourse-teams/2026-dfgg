package dfgg.application.recommend.v3;

/**
 * 서로 다른 근거로 후보를 발견하는 generator. 구현체는 정확히 4개다.
 *
 * <p>generator는 최종 순위를 결정하지 않는다. 목적은 recall — 각자 다른 이유로 정답 후보를
 * 찾아내 union을 두텁게 하는 것이고, 그 근거들 사이의 trade-off는 LTR이 학습한다.
 * 따라서 여기서 나온 score를 가중합하거나 서로 비교하려 들면 안 된다.
 */
public interface CandidateGenerator {

    CandidateSource source();

    /**
     * @param topK 이 generator가 낼 후보 수 상한. 값은 Recall@K 실험으로 정하며 설정에서 온다.
     */
    GeneratorResult generate(RecommendationQuery query, int topK);
}
