package dfgg.domain.match;

/** 한 선수가 Core 하나를 얻은 시각. 구매 순서와 함께 쓰여 "k번째 Core를 산 순간"을 가리킨다. */
public record CoreItemAcquisition(int itemId, long gameTimeMs) {
}
