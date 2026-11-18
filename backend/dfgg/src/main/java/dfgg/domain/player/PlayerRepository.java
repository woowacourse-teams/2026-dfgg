package dfgg.domain.player;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, String> {

    @Query("""
            SELECT player.puuid
            FROM Player player
            WHERE player.platform = :platform
            ORDER BY player.puuid
            """)
    List<String> findPuuidsByPlatform(
            @Param("platform") String platform,
            Pageable pageable
    );

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
