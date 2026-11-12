package dfgg.application.propick;

/** Leaguepedia 선수 한 명. 팀·사진 URL은 비어 있을 수 있다. */
public record ProPlayerInfo(
        String page,
        String name,
        String team,
        String imageUrl
) {
}
