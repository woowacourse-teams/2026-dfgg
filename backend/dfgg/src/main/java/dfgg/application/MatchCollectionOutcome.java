package dfgg.application;

/** 자동 수집이 매치 하나를 처리한 결과. 메트릭 태그 값을 함께 가진다. */
enum MatchCollectionOutcome {

    /** 원본을 새로 받아 참가자 정규화·통계까지 끝냈다. */
    COLLECTED("collected"),
    /** 원본이 이미 있어 아무것도 하지 않았다. */
    DUPLICATE("duplicate"),
    /** Match 또는 Timeline 원본을 받지 못했다. */
    COLLECTION_FAILED("collection_failed"),
    /** 원본은 받았지만 참가자 정규화나 저장에 실패했다. */
    NORMALIZATION_FAILED("normalization_failed");

    private final String tag;

    MatchCollectionOutcome(String tag) {
        this.tag = tag;
    }

    String tag() {
        return tag;
    }
}
