package dfgg.presentation.dto;

/**
 * 응답에 싣는 룬·계열·파편 하나.
 * 카탈로그에 아직 없는 id(새 패치 직후 동기화 전)는 id만 내고 이름과 이미지를 비운다.
 */
public record RuneDto(int id, String name, String imageUrl) {

    public static RuneDto unknown(int id) {
        return new RuneDto(id, null, null);
    }
}
