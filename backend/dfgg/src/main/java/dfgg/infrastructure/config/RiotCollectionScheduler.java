package dfgg.infrastructure.config;

import dfgg.application.RiotCollectionOrchestrator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 설정된 주기에 맞춰 Riot 데이터 수집을 시작한다.
 *
 * <p>여러 애플리케이션 인스턴스에서 같은 스케줄이 실행될 수 있으므로,
 * PostgreSQL 세션 단위 advisory lock을 획득한 인스턴스만 실제 수집을 수행한다.
 */
@Component
@ConditionalOnProperty(prefix = "collection.scheduler", name = "enabled", havingValue = "true")
public class RiotCollectionScheduler {

    // 모든 애플리케이션 인스턴스가 공유하는 스케줄러 잠금 식별자다.
    private static final long SCHEDULER_LOCK_ID = 0x44464747L;
    // 세션이 잠금을 획득했는지 확인하는 PostgreSQL advisory lock 쿼리다.
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    // 수집이 끝난 뒤 같은 DB 세션에서 잠금을 해제하는 쿼리다.
    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private final RiotCollectionOrchestrator orchestrator;
    private final DataSource dataSource;

    public RiotCollectionScheduler(
            RiotCollectionOrchestrator orchestrator,
            DataSource dataSource
    ) {
        this.orchestrator = orchestrator;
        this.dataSource = dataSource;
    }

    @Scheduled(
            cron = "${collection.scheduler.cron:0 */5 * * * *}",
            zone = "${collection.scheduler.zone:Asia/Seoul}"
    )
    public void collect() {
        // 잠금의 소유권이 연결에 묶이므로, 획득부터 해제까지 같은 Connection을 유지한다.
        try (Connection connection = dataSource.getConnection()) {
            // 다른 인스턴스가 이미 수집 중이면 이번 실행은 건너뛰고 다음 스케줄을 기다린다.
            if (!executeLockQuery(connection, TRY_LOCK_SQL)) {
                return;
            }
            try {
                // 수집 흐름의 세부 단계는 Orchestrator에 위임한다.
                orchestrator.runOnce();
            } finally {
                // 수집 성공 여부와 관계없이 세션 잠금을 해제한다.
                unlock(connection);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to coordinate Riot collection scheduler", exception);
        }
    }

    /**
     * 수집이 끝난 DB 세션에서 scheduler advisory lock을 해제한다.
     */
    private void unlock(Connection connection) {
        try {
            executeLockQuery(connection, UNLOCK_SQL);
        } catch (SQLException exception) {
            abortConnection(connection, exception);
        }
    }

    /**
     * 잠금 해제에 실패해 연결이 잠금을 계속 보유할 가능성이 있으면 연결을 중단한다.
     * 연결 중단은 PostgreSQL 세션을 종료해 남아 있는 advisory lock도 함께 정리한다.
     */
    private void abortConnection(Connection connection, SQLException unlockException) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException abortException) {
            unlockException.addSuppressed(abortException);
        }
    }

    /**
     * 전달받은 PostgreSQL advisory lock 쿼리를 실행하고 결과 Boolean을 반환한다.
     * TRY_LOCK은 획득 성공 여부를, UNLOCK은 현재 세션이 잠금을 보유했는지를 나타낸다.
     */
    private boolean executeLockQuery(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, SCHEDULER_LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }
}
