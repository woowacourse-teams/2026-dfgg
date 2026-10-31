package dfgg.application.rune;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.PatchVersion;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.rune.NormalizedMatchParticipantRune;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import dfgg.infrastructure.external.dto.MatchInfo;
import dfgg.infrastructure.external.dto.MatchParticipant;
import dfgg.infrastructure.external.dto.MatchResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 Raw Match에서 참가자별 룬 페이지를 꺼내 한 경기 단위로 적재한다. 참가자 정규화와는 독립적이다. */
@Service
public class ParticipantRuneNormalizationService {

    private final RawMatchRepository rawMatchRepository;
    private final NormalizedMatchParticipantRuneRepository runeRepository;
    private final ObjectMapper objectMapper;

    public ParticipantRuneNormalizationService(RawMatchRepository rawMatchRepository,
                                               NormalizedMatchParticipantRuneRepository runeRepository,
                                               ObjectMapper objectMapper) {
        this.rawMatchRepository = rawMatchRepository;
        this.runeRepository = runeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 한 경기의 룬을 원본에서 다시 만들어 저장하고 저장한 참가자 수를 돌려준다.
     * 이미 저장된 참가자는 덮어쓰고, 참가자 한 명이라도 룬을 읽지 못하면 경기 전체를 저장하지 않는다.
     */
    @Transactional
    public int normalize(String matchId) {
        MatchInfo info = readInfo(matchId);
        String patch = PatchVersion.of(info.gameVersion()).value();
        List<NormalizedMatchParticipantRune> runes = info.participants().stream()
                .map(participant -> toRune(matchId, patch, participant))
                .toList();
        runeRepository.upsertAll(runes);
        return runes.size();
    }

    private MatchInfo readInfo(String matchId) {
        RawMatch rawMatch = rawMatchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("저장된 Raw Match가 없습니다: " + matchId));
        try {
            MatchInfo info = objectMapper.readValue(rawMatch.getRawData(), MatchResponse.class).info();
            if (info == null || info.participants() == null || info.participants().isEmpty()) {
                throw new IllegalArgumentException("Raw Match의 참가자 목록이 없습니다: " + matchId);
            }
            return info;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Raw Match JSON을 읽을 수 없습니다: " + matchId, exception);
        }
    }

    private NormalizedMatchParticipantRune toRune(String matchId, String patch, MatchParticipant participant) {
        if (participant.perks() == null) {
            throw new IllegalArgumentException("Raw Match 참가자의 룬(perks)이 없습니다: " + participant.participantId());
        }
        return new NormalizedMatchParticipantRune(matchId, participant.participantId(), patch,
                participant.championId(), participant.teamId(), participant.teamPosition(), participant.win(),
                participant.perks().toRunePage());
    }
}
