package dfgg.domain.player;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, String> {

    @Modifying
    @Query(value = """
            INSERT INTO players (puuid, platform, first_seen_at, last_seen_at)
            VALUES (:puuid, :platform, :seenAt, :seenAt)
            ON CONFLICT (puuid) DO UPDATE SET
                platform = CASE
                    WHEN EXCLUDED.last_seen_at >= players.last_seen_at THEN EXCLUDED.platform
                    ELSE players.platform
                END,
                first_seen_at = LEAST(players.first_seen_at, EXCLUDED.first_seen_at),
                last_seen_at = GREATEST(players.last_seen_at, EXCLUDED.last_seen_at)
            """, nativeQuery = true)
    void upsert(
            @Param("puuid") String puuid,
            @Param("platform") String platform,
            @Param("seenAt") Instant seenAt
    );
}
