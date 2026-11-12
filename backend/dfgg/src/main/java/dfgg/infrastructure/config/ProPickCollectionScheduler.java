package dfgg.infrastructure.config;

import dfgg.application.propick.ProLeagueCollectionResult;
import dfgg.application.propick.ProPickCollectionService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 설정된 주기에 맞춰 Leaguepedia 대회 픽 수집을 시작한다.
 *
 * <p>여러 애플리케이션 인스턴스에서 같은 스케줄이 실행될 수 있으므로,
 * PostgreSQL 세션 단위 advisory lock을 획득한 인스턴스만 실제 수집을 수행한다.
 */
@Component
@ConditionalOnProperty(prefix = "pro-picks.scheduler", name = "enabled", havingValue = "true")
public class ProPickCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProPickCollectionScheduler.class);

    // 대회 픽 수집 잠금 식별자다. 매치 수집 잠금과 달라서 두 수집이 서로를 막지 않는다.
    static final long LOCK_ID = 0x50524F50L;
    // 세션이 잠금을 획득했는지 확인하는 PostgreSQL advisory lock 쿼리다.
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    // 수집이 끝난 뒤 같은 DB 세션에서 잠금을 해제하는 쿼리다.
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private final ProPickCollectionService collectionService;
    private final DataSource dataSource;

    public ProPickCollectionScheduler(ProPickCollectionService collectionService, DataSource dataSource) {
        this.collectionService = collectionService;
        this.dataSource = dataSource;
    }

    // 이전 실행이 끝난 시각부터 지연을 센다. 첫 실행은 앱이 뜬 뒤 지연을 두어 배포마다 바로 Leaguepedia를 부르지 않는다.
    @Scheduled(fixedDelayString = "${pro-picks.scheduler.fixed-delay:6h}",
            initialDelayString = "${pro-picks.scheduler.initial-delay:5m}")
    public void collect() {
        long startedAt = System.nanoTime();
        // 잠금의 소유권이 연결에 묶이므로, 획득부터 해제까지 같은 Connection을 유지한다.
        try (Connection connection = dataSource.getConnection()) {
            if (!executeLockQuery(connection, TRY_LOCK_SQL)) {
                log.info("대회 픽 수집 스케줄 실행 건너뜀: 다른 인스턴스가 실행 중입니다.");
                return;
            }
            try {
                log.info("대회 픽 수집 스케줄 실행 시작");
                collectionService.collect().leagues().forEach(this::logLeague);
                log.info("대회 픽 수집 스케줄 실행 완료: elapsedMs={}", elapsedMillis(startedAt));
            } catch (RuntimeException exception) {
                log.error("대회 픽 수집 스케줄 실행 실패: elapsedMs={}", elapsedMillis(startedAt), exception);
                throw exception;
            } finally {
                // 수집 성공 여부와 관계없이 세션 잠금을 해제한다.
                unlock(connection);
            }
        } catch (SQLException exception) {
            log.error("대회 픽 수집 스케줄 조정 실패", exception);
            throw new IllegalStateException("Failed to coordinate pro pick collection scheduler", exception);
        }
    }

    private void logLeague(ProLeagueCollectionResult league) {
        if (league.failure() != null) {
            log.warn("대회 픽 수집 리그 실패: league={}, failure={}", league.league(), league.failure());
            return;
        }
        log.info("대회 픽 수집 리그 결과: league={}, fetchedRows={}, savedPicks={}, skippedRows={}",
                league.league(), league.fetchedRows(), league.savedPicks(), league.skippedRows());
    }

    private void unlock(Connection connection) {
        try {
            if (!executeLockQuery(connection, UNLOCK_SQL)) {
                log.warn("대회 픽 수집 스케줄 분산 락을 해제하지 못했습니다: lock not held");
            }
        } catch (SQLException exception) {
            log.error("대회 픽 수집 스케줄 분산 락 해제 실패", exception);
            abortConnection(connection, exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 잠금 해제에 실패해 연결이 잠금을 계속 보유할 수 있으면 연결을 끊는다. 세션이 끝나면 advisory lock도 풀린다.
     */
    private void abortConnection(Connection connection, SQLException unlockException) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException abortException) {
            unlockException.addSuppressed(abortException);
        }
    }

    private boolean executeLockQuery(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }
}
