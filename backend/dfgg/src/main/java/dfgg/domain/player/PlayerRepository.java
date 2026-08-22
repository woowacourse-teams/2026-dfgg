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
            ON CONFLICT (puuid) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("puuid") String puuid,
            @Param("platform") String platform,
            @Param("seenAt") Instant seenAt
    );

    @Modifying
    @Query(value = """
            UPDATE players SET
                platform = CASE
                    WHEN :seenAt >= last_seen_at THEN :platform
                    ELSE players.platform
                END,
                first_seen_at = LEAST(first_seen_at, :seenAt),
                last_seen_at = GREATEST(last_seen_at, :seenAt)
            WHERE puuid = :puuid
            """, nativeQuery = true)
    int updateObservation(
            @Param("puuid") String puuid,
            @Param("platform") String platform,
            @Param("seenAt") Instant seenAt
    );
}
