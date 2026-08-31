package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.RiotCollectionOrchestrator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

@ExtendWith(OutputCaptureExtension.class)
class RiotSchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RiotCollectionOrchestrator.class, () -> mock(RiotCollectionOrchestrator.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withUserConfiguration(
                    RiotSchedulingConfiguration.class,
                    RiotCollectionScheduler.class
            );

    @Test
    void 기본_cron은_5분_주기다() {
        assertThat(new RiotSchedulerProperties().getCron()).isEqualTo("0 */5 * * * *");
    }

    @Test
    void enabled가_false이면_스케줄러를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("collection.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
                    assertThat(context).doesNotHaveBean(RiotCollectionScheduler.class);
                });
    }

    @Test
    void enabled가_true이면_스케줄러를_등록한다() {
        contextRunner
                .withPropertyValues("collection.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
                    assertThat(context).hasSingleBean(RiotCollectionScheduler.class);
                });
    }

    @Test
    void cron_timezone과_수집_범위를_설정으로_bind한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "collection.scheduler.enabled=true",
                        "collection.scheduler.cron=0 30 4 * * *",
                        "collection.scheduler.zone=UTC",
                        "collection.scheduler.tiers=EMERALD",
                        "collection.scheduler.divisions=I,II",
                        "collection.scheduler.player-limit=3",
                        "collection.scheduler.match-count=50",
                        "collection.scheduler.recover-missing-timelines=true"
                )
                .run(context -> {
                    RiotSchedulerProperties properties = context.getBean(RiotSchedulerProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getCron()).isEqualTo("0 30 4 * * *");
                    assertThat(properties.getZone()).isEqualTo("UTC");
                    assertThat(properties.getTiers()).containsExactly("EMERALD");
                    assertThat(properties.getDivisions()).containsExactly("I", "II");
                    assertThat(properties.getPlayerLimit()).isEqualTo(3);
                    assertThat(properties.getMatchCount()).isEqualTo(50);
                    assertThat(properties.isRecoverMissingTimelines()).isTrue();
                });
    }

    @Test
    void Master_티어를_수집_설정으로_bind한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("collection.scheduler.tiers=MASTER")
                .run(context -> {
                    RiotSchedulerProperties properties = context.getBean(RiotSchedulerProperties.class);
                    assertThat(properties.getTiers()).containsExactly("MASTER");
                });
    }

    @Test
    void Grandmaster_티어를_수집_설정으로_bind한다() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("collection.scheduler.tiers=GRANDMASTER")
                .run(context -> {
                    RiotSchedulerProperties properties = context.getBean(RiotSchedulerProperties.class);
                    assertThat(properties.getTiers()).containsExactly("GRANDMASTER");
                });
    }

    @Test
    void 분산_락을_획득한_인스턴스만_수집한다(CapturedOutput output) throws Exception {
        RiotCollectionOrchestrator orchestrator = mock(RiotCollectionOrchestrator.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        ResultSet lockResult = result(true);
        ResultSet unlockResult = result(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)"))
                .thenReturn(unlockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(unlockStatement.executeQuery()).thenReturn(unlockResult);

        new RiotCollectionScheduler(orchestrator, dataSource).collect();

        verify(orchestrator).runOnce();
        verify(connection).prepareStatement("SELECT pg_advisory_unlock(?)");
        assertThat(output).contains(
                "Riot 데이터 수집 스케줄 실행 시작",
                "Riot 데이터 수집 스케줄 실행 완료: elapsedMs="
        );
    }

    @Test
    void 다른_인스턴스가_분산_락을_보유하면_수집하지_않는다(CapturedOutput output) throws Exception {
        RiotCollectionOrchestrator orchestrator = mock(RiotCollectionOrchestrator.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        ResultSet lockResult = result(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(lockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);

        new RiotCollectionScheduler(orchestrator, dataSource).collect();

        verifyNoInteractions(orchestrator);
        assertThat(output).contains(
                "Riot 데이터 수집 스케줄 실행 건너뜀: 다른 인스턴스가 실행 중입니다."
        );
    }

    @Test
    void 수집이_실패해도_분산_락을_해제한다() throws Exception {
        RiotCollectionOrchestrator orchestrator = mock(RiotCollectionOrchestrator.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        ResultSet lockResult = result(true);
        ResultSet unlockResult = result(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)"))
                .thenReturn(unlockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(unlockStatement.executeQuery()).thenReturn(unlockResult);
        IllegalStateException failure = new IllegalStateException("collection failed");
        doThrow(failure).when(orchestrator).runOnce();

        assertThatThrownBy(() -> new RiotCollectionScheduler(orchestrator, dataSource).collect())
                .isSameAs(failure);

        verify(connection).prepareStatement("SELECT pg_advisory_unlock(?)");
    }

    @Test
    void 분산_락_해제에_실패하면_연결을_폐기한다() throws Exception {
        RiotCollectionOrchestrator orchestrator = mock(RiotCollectionOrchestrator.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        ResultSet lockResult = result(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
                .thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)"))
                .thenReturn(unlockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(unlockStatement.executeQuery()).thenThrow(new java.sql.SQLException("unlock failed"));

        new RiotCollectionScheduler(orchestrator, dataSource).collect();

        verify(connection).abort(any());
    }

    private ResultSet result(boolean value) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(value);
        return resultSet;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RiotSchedulerProperties.class)
    static class PropertiesConfiguration {
    }
}
