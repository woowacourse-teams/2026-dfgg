package dfgg.presentation;

import dfgg.application.rune.RuneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class RuneController {

    private final RuneService runeService;

    public RuneController(RuneService runeService) {
        this.runeService = runeService;
    }

    /** 새 패치가 나오면 실행한다. 룬 이름을 DB에, 아이콘을 S3의 그 패치 폴더에 저장한다. */
    @PostMapping("/runes")
    public ResponseEntity<Void> syncRunes() {
        runeService.syncRunes();
        return ResponseEntity.noContent().build();
    }
}
