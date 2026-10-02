package dfgg.domain.item.trait;

/**
 * 통계만으로는 "이 아이템에 아군이 붙어도 되는가"를 가를 수 없어서 도메인 판단으로 선언한다
 */
public enum Synergy {
    /** 아군에게 작용한다. 아군 조합이 추천 이유가 될 수 있다. */
    ALLY,
    /** 자기에게만 작용한다. 선언하지 않은 아이템의 기본값이다. */
    SELF,
}
