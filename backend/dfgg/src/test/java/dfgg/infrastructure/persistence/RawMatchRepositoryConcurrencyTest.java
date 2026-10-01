package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.RawMatchRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RawMatchRepositoryConcurrencyTest {

    @Autowired
    private RawMatchRepository rawMatchRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        rawMatchRepository.deleteAll();
    }

    @Test
    void 같은_매치를_동시에_삽입해도_한_건만_저장한다() throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> insertAfterSignal(startSignal));
            Future<Integer> second = executor.submit(() -> insertAfterSignal(startSignal));

            startSignal.countDown();

            assertThat(List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(1, 0);
        }

        assertThat(rawMatchRepository.count()).isEqualTo(1);
    }

    private int insertAfterSignal(CountDownLatch startSignal) {
        try {
            startSignal.await();
            return rawMatchRepository.insertIfAbsent(
                    "KR_1234567890",
                    "{\"info\":{}}"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent persistence test was interrupted", exception);
        }
    }
}
