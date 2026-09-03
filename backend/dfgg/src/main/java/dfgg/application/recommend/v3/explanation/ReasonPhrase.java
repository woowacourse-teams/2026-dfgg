package dfgg.application.recommend.v3.explanation;

/**
 * 한 묶음을 사람 말로 옮긴 문구.
 * <p>
 * 한국어는 절을 이어붙일 때 어미가 달라진다.
 * 연결형과 종결형을 따로 들고 있지 않으면
 * "…잘 맞고 추천했어요"처럼 어색하거나 "…잘 맞아 상대 조합에…"처럼 끊긴 문장이 나온다.
 *
 * @param connective 뒤에 다른 절이 이어질 때 (…맞고)
 * @param terminal   문장을 닫을 때 (…맞아)
 * @param caveat     점수를 끌어내렸을 때 붙이는 말
 */
public record ReasonPhrase(String connective, String terminal, String caveat) {
}
