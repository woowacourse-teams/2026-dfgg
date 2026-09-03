package dfgg.evaluation;

import dfgg.domain.match.NormalizedMatchParticipant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 매치에서 평가에 쓸 참가자를 편향 없이 고른다.
 *
 * <p>앞에서부터 자르면 안 된다. Riot 데이터의 {@code participantId}는 포지션과 사실상 1:1로
 * 묶여 있어(실측: id 1은 99.8%가 TOP, id 2는 99.95%가 JUNGLE) 앞의 2명을 쓰면 TOP·JUNGLE만
 * 평가하게 된다. 실제로 그렇게 측정했다가 MID·BOTTOM·SUPPORT가 통째로 빠진 상태로
 * "Ally-Synergy는 기여가 없다"는 결론을 낼 뻔했다 — Ally-Synergy의 존재 이유가 바로
 * 서포터인데 서포터를 한 번도 안 본 것이다.
 *
 * <p>매치 ID를 시드로 셔플해 포지션·팀에 고루 퍼지게 하되, 같은 매치는 항상 같은 참가자를
 * 뽑아 평가를 재현할 수 있게 한다.
 */
public final class ParticipantSampler {

    public List<NormalizedMatchParticipant> sample(
            List<NormalizedMatchParticipant> matchParticipants, String matchId, int sampleSize
    ) {
        List<NormalizedMatchParticipant> shuffled = new ArrayList<>(matchParticipants);
        Collections.shuffle(shuffled, new Random(matchId.hashCode()));
        return shuffled.subList(0, Math.min(sampleSize, shuffled.size()));
    }
}
