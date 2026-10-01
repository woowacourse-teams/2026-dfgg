package dfgg.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ItemRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 아이템을_저장하고_조회한다() {
        // given
        Item item = new Item(3071L, "칠흑의 양날 도끼");

        // when
        itemRepository.save(item);
        entityManager.flush();
        entityManager.clear();

        // then
        Item saved = itemRepository.findById(3071L).orElseThrow();
        assertThat(saved.getItemId()).isEqualTo(3071L);
        assertThat(saved.getName()).isEqualTo("칠흑의 양날 도끼");
    }

    @Test
    void 같은_ID의_아이템을_다시_저장하면_기존_데이터를_갱신한다() {
        // given
        itemRepository.save(new Item(3071L, "이전 이름"));
        entityManager.flush();
        entityManager.clear();

        // when
        itemRepository.saveAll(List.of(new Item(3071L, "칠흑의 양날 도끼")));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(itemRepository.count()).isEqualTo(1);

        Item updated = itemRepository.findById(3071L).orElseThrow();
        assertThat(updated.getName()).isEqualTo("칠흑의 양날 도끼");
    }
}
