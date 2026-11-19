package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.RiotCollectionOrchestrator;
import dfgg.application.propick.ProLeagueCollectionResult;
import dfgg.application.propick.ProPickCollectionResult;
import dfgg.application.propick.ProPickCollectionService;
import dfgg.application.propick.ProPickSkipReason;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;

@ExtendWith(OutputCaptureExtension.class)
class ProPickSchedulingConfigurationTest {

    private static final String TRY_LOCK = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK = "SELECT pg_advisory_unlock(?)";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProPickCollectionService.class, () -> mock(ProPickCollectionService.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withUserConfiguration(ProPickSchedulingConfiguration.class, ProPickCollectionScheduler.class);

    @Test
    @DisplayName("설정이 없으면 대회 픽 수집 스케줄러를 켜지 않는다 — 기본은 끔")
    void scheduler_WhenEnabledNotSet_NotRegistered() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            assertThat(context).doesNotHaveBean(ProPickCollectionScheduler.class);
        });
    }

    @Test
    @DisplayName("매치 수집 스케줄러만 켜면 대회 픽 수집 스케줄러는 켜지지 않는다 — 따로 켜고 끈다")
    void scheduler_WhenOnlyRiotCollectionEnabled_NotRegistered() {
        new ApplicationContextRunner()
                .withBean(ProPickCollectionService.class, () -> mock(ProPickCollectionService.class))
                .withBean(RiotCollectionOrchestrator.class, () -> mock(RiotCollectionOrchestrator.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withUserConfiguration(ProPickSchedulingConfiguration.class, ProPickCollectionScheduler.class,
                        RiotSchedulingConfiguration.class, RiotCollectionScheduler.class)
                .withPropertyValues("collection.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RiotCollectionScheduler.class);
                    assertThat(context).doesNotHaveBean(ProPickCollectionScheduler.class);
                });
    }

    @Test
    @DisplayName("켜면 이전 실행이 끝난 뒤 6시간마다 돌고, 첫 실행은 앱이 뜨고 5분 뒤다")
    void scheduler_WhenEnabled_RunEverySixHoursAfterFiveMinutes() {
        contextRunner
                .withPropertyValues("pro-picks.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ProPickCollectionScheduler.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                            .singleElement()
                            .extracting(ScheduledTask::getTask)
                            .isInstanceOfSatisfying(FixedDelayTask.class, task -> {
                                assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofHours(6));
                                assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(5));
                            });
                });
    }

    @Test
    @DisplayName("주기와 첫 실행 지연은 설정으로 바꿀 수 있다")
    void scheduler_WhenDelaysConfigured_UseConfiguredDelays() {
        contextRunner
                .withPropertyValues(
                        "pro-picks.scheduler.enabled=true",
                        "pro-picks.scheduler.fixed-delay=30m",
                        "pro-picks.scheduler.initial-delay=10s")
                .run(context -> assertThat(
                        context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks())
                        .singleElement()
                        .extracting(ScheduledTask::getTask)
                        .isInstanceOfSatisfying(FixedDelayTask.class, task -> {
                            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(30));
                            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(10));
                        }));
    }

    @Test
    @DisplayName("잠금을 얻은 인스턴스만 수집하고, 리그별 결과를 로그로 남긴 뒤 잠금을 푼다")
    void collect_WhenLockAcquired_CollectAndLogPerLeague(CapturedOutput output) throws Exception {
        // given
        ProPickCollectionService collectionService = mock(ProPickCollectionService.class);
        when(collectionService.collect()).thenReturn(new ProPickCollectionResult(List.of(
                new ProLeagueCollectionResult("LCK/", 120, 110, Map.of(ProPickSkipReason.UNKNOWN_CHAMPION, 2), null),
                new ProLeagueCollectionResult("LPL/", 0, 0, Map.of(), "Leaguepedia 재시도 3회를 넘겼습니다"))));
        DataSource dataSource = mock(DataSource.class);
        Connection connection = lockedConnection(dataSource, true);

        // when
        new ProPickCollectionScheduler(collectionService, dataSource).collect();

        // then
        verify(collectionService).collect();
        verify(connection).prepareStatement(UNLOCK);
        assertThat(output).contains(
                "대회 픽 수집 스케줄 실행 시작",
                "league=LCK/, fetchedRows=120, savedPicks=110",
                "league=LPL/",
                "Leaguepedia 재시도 3회를 넘겼습니다",
                "대회 픽 수집 스케줄 실행 완료");
    }

    @Test
    @DisplayName("다른 인스턴스가 잠금을 갖고 있으면 수집하지 않는다")
    void collect_WhenLockHeldElsewhere_SkipCollection(CapturedOutput output) throws Exception {
        // given
        ProPickCollectionService collectionService = mock(ProPickCollectionService.class);
        DataSource dataSource = mock(DataSource.class);
        lockedConnection(dataSource, false);

        // when
        new ProPickCollectionScheduler(collectionService, dataSource).collect();

        // then
        verifyNoInteractions(collectionService);
        assertThat(output).contains("대회 픽 수집 스케줄 실행 건너뜀: 다른 인스턴스가 실행 중입니다.");
    }

    @Test
    @DisplayName("수집이 실패해도 잠금을 푼다")
    void collect_WhenCollectionFails_ReleaseLock() throws Exception {
        // given
        ProPickCollectionService collectionService = mock(ProPickCollectionService.class);
        IllegalStateException failure = new IllegalStateException("collection failed");
        doThrow(failure).when(collectionService).collect();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = lockedConnection(dataSource, true);

        // when & then
        assertThatThrownBy(() -> new ProPickCollectionScheduler(collectionService, dataSource).collect())
                .isSameAs(failure);
        verify(connection).prepareStatement(UNLOCK);
    }

    @Test
    @DisplayName("매치 수집과 다른 잠금을 쓴다 — 두 수집이 서로를 막지 않는다")
    void lockId_WhenComparedWithRiotScheduler_Differ() {
        assertThat(ProPickCollectionScheduler.LOCK_ID).isNotEqualTo(0x44464747L);
    }

    private Connection lockedConnection(DataSource dataSource, boolean acquired) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement lockStatement = mock(PreparedStatement.class);
        PreparedStatement unlockStatement = mock(PreparedStatement.class);
        ResultSet lockResult = result(acquired);
        ResultSet unlockResult = result(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(TRY_LOCK)).thenReturn(lockStatement);
        when(connection.prepareStatement(UNLOCK)).thenReturn(unlockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(unlockStatement.executeQuery()).thenReturn(unlockResult);
        return connection;
    }

    private ResultSet result(boolean value) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(value);
        return resultSet;
    }
}
