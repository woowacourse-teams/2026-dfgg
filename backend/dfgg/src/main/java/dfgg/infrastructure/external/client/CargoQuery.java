package dfgg.infrastructure.external.client;

/**
 * Leaguepedia {@code cargoquery} 한 건. 페이지 크기·offset·maxlag는 클라이언트가 붙인다.
 *
 * @param joinOn 여러 테이블을 조인할 때만 쓴다 (예: {@code PI.Tournament=T.OverviewPage}). 없으면 null
 */
public record CargoQuery(
        String tables,
        String fields,
        String where,
        String orderBy,
        String joinOn
) {

    public CargoQuery(String tables, String fields, String where, String orderBy) {
        this(tables, fields, where, orderBy, null);
    }

    public boolean hasJoin() {
        return joinOn != null && !joinOn.isBlank();
    }
}
