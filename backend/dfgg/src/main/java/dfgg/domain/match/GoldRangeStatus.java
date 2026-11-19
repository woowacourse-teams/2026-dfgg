package dfgg.domain.match;

/** 구매 직전 Gold Range를 계산할 때 사용한 관측 근거다. 실제 구매 순간 Gold의 정확도 판정은 아니다. */
public enum GoldRangeStatus {

    /** 앞뒤 프레임의 currentGold와 아이템 거래액 구간으로 만든 기본 범위다. */
    BASE,

    /** 프레임의 totalGold 증가량이 거래 흐름과 양립해 기본 범위를 보정했다. */
    TOTAL_GOLD_REFINED
}
