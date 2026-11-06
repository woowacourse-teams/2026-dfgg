package dfgg.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 화면에서 읽은 후보 증강 이름. 증강 선택 화면의 후보는 최대 3개다. 못 찾은 이름은 그대로 돌려주므로 길이도 막는다. */
public record AugmentLookupRequest(
        @NotEmpty(message = "증강 이름은 필수입니다")
        @Size(max = 3, message = "증강 이름은 3개까지 보낼 수 있습니다")
        List<@NotBlank(message = "증강 이름은 비어 있을 수 없습니다")
             @Size(max = 100, message = "증강 이름은 100자까지 보낼 수 있습니다") String> names
) {
}
