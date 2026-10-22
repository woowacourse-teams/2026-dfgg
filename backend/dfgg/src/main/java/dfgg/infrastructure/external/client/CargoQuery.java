package dfgg.infrastructure.external.client;

/**
 * Leaguepedia {@code cargoquery} 한 건. 페이지 크기·offset·maxlag는 클라이언트가 붙인다.
 */
public record CargoQuery(
        String tables,
        String fields,
        String where,
        String orderBy
) {
}
