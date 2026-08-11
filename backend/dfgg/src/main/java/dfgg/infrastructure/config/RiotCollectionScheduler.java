package dfgg.infrastructure.config;

import dfgg.application.RiotCollectionOrchestrator;
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

@Component
@ConditionalOnProperty(prefix = "collection.scheduler", name = "enabled", havingValue = "true")
public class RiotCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RiotCollectionScheduler.class);
    private static final long SCHEDULER_LOCK_ID = 0x44464747L;
    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
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
        try (Connection connection = dataSource.getConnection()) {
            if (!executeLockQuery(connection, TRY_LOCK_SQL)) {
                log.info("Riot collection scheduler skipped: another instance is running");
                return;
            }
            try {
                orchestrator.runOnce();
            } finally {
                unlock(connection);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to coordinate Riot collection scheduler", exception);
        }
    }

    private void unlock(Connection connection) {
        try {
            if (!executeLockQuery(connection, UNLOCK_SQL)) {
                log.warn("Riot collection scheduler lock was not owned during release");
            }
        } catch (SQLException exception) {
            log.warn("Failed to release Riot collection scheduler lock", exception);
            abortConnection(connection, exception);
        }
    }

    private void abortConnection(Connection connection, SQLException unlockException) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException abortException) {
            unlockException.addSuppressed(abortException);
            log.warn("Failed to abort connection holding Riot scheduler lock", abortException);
        }
    }

    private boolean executeLockQuery(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, SCHEDULER_LOCK_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }
}
