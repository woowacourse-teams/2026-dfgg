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

    /**
     * 패치가 나오고 약 10일 뒤에 실행한다. 증강 픽률과 이름을 DB에, 아이콘을 S3의 그 패치 폴더에 저장한다.
     * <p>
     * 그보다 이르면 새 증강의 표본이 적어 픽률이 실제보다 낮다(16.21 기준). 같은 패치는 다시 실행해도 교체된다.
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        aramMayhemSyncService.sync();
        return ResponseEntity.noContent().build();
    }
}
