package dfgg.application.aggregation;

/**
 * 전체 집계({@code POST /admin/mining/all})의 한 단계. 새 통계 테이블은 이 구현을 하나 추가해 등록한다.
 * 단계마다 자기 트랜잭션으로 끝나고, 다시 실행해도 같은 결과여야 한다.
 */
public interface StatsAggregationStep {

    /** 응답과 실패 알림에 쓰는 이름. 단계마다 달라야 한다. */
    String name();

    /** 실행 순서. 작은 값부터 실행하고, 단계마다 달라야 한다. */
    int order();

    /** 집계를 실행하고 응답에 담을 결과를 돌려준다. */
    Object aggregate();
}
