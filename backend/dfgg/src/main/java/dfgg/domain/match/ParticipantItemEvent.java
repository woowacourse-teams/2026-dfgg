package dfgg.domain.match;

/**
 * Raw Timeline에서 추출한 내부 원천 이벤트. 별도 이벤트 테이블에는 저장하지 않는다.
 * 구매 레코드 생성과 후속 인벤토리 복원에서 재사용한다.
 * sourcePayload는 미지원 필드도 보존하는 원본 이벤트의 JSON이다.
 *
 * @param eventOrder 참가자별 전체 아이템 행동 순서. 판매·제거·취소를 포함해 상태 복원에 사용한다.
 * @param purchaseOrder 참가자별 구매 순서. 비구매는 null이며, 취소된 구매의 순번도 유지한다.
 */
public record ParticipantItemEvent(
        String matchId,
        int participantId,
        int eventOrder,
        Integer purchaseOrder,
        int sourceFrameIndex,
        int sourceEventIndex,
        String eventType,
        Integer itemId,
        Integer beforeItemId,
        Integer afterItemId,
        Long gameTimeMs,
        String sourcePayload,
        String normalizationVersion
) {
}
