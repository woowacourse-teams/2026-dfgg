package dfgg.domain.aram;

/** 챔피언의 증강 하나와 그 등급 안의 순위. */
public record RankedAugment(Augment augment, int rank, double pickRate) {

    /** 응답에 싣는 픽률. 순위는 반올림 전 값으로 매긴다. */
    public double displayedPickRate() {
        return Math.round(pickRate * 10) / 10.0;
    }
}
