package dfgg.domain.match;

/** 운영 중 조정하는 Situational 적용 기준. 조회표 하한보다 낮으면 표에 없는 구간을 쓰게 되므로 거절한다. */
public record SituationalThresholds(double minLift, double minBaseRate) {
    public SituationalThresholds {
        if (!(minLift >= SituationalComponentPolicy.LIFT_FLOOR) || !Double.isFinite(minLift)
                || !(minBaseRate >= SituationalComponentPolicy.BASE_RATE_FLOOR) || minBaseRate > 1) {
            throw new IllegalArgumentException("Situational 기준은 조회표 하한(lift "
                    + SituationalComponentPolicy.LIFT_FLOOR + ", 기본 구매율 " + SituationalComponentPolicy.BASE_RATE_FLOOR + ") 이상이어야 합니다.");
        }
    }
}
