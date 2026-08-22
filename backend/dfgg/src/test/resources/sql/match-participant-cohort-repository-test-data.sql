DELETE FROM match_participant_cohorts;

INSERT INTO match_participant_cohorts (match_id, puuid, queue_type, tier, division, collected_at) VALUES
    ('KR_M1', 'puuid-1', 'RANKED_SOLO_5x5', 'GOLD', 'II', '2026-08-01T00:00:00Z'),
    ('KR_M1', 'puuid-2', 'RANKED_SOLO_5x5', 'SILVER', 'I', '2026-08-01T00:00:00Z'),
    ('KR_M1', 'puuid-1', 'RANKED_FLEX_SR', 'PLATINUM', 'IV', '2026-08-01T00:00:00Z'),
    ('KR_M2', 'puuid-3', 'RANKED_SOLO_5x5', 'GOLD', 'III', '2026-08-01T00:00:00Z');
