package dfgg.presentation;

import dfgg.application.aram.AramMayhemSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/aram-mayhem")
public class AramMayhemAdminController {

    private final AramMayhemSyncService aramMayhemSyncService;

    public AramMayhemAdminController(AramMayhemSyncService aramMayhemSyncService) {
        this.aramMayhemSyncService = aramMayhemSyncService;
    }

    /** 패치가 나오면 실행한다. 증강 픽률과 이름을 DB에, 아이콘을 S3의 그 패치 폴더에 저장한다. */
    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        aramMayhemSyncService.sync();
        return ResponseEntity.noContent().build();
    }
}
