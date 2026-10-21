package dfgg.infrastructure.external.dto;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record GeminiGenerateContentResponse(List<Candidate> candidates) {

    /** 첫 후보의 답변 텍스트. 후보가 없거나 안전 필터로 막혀 텍스트가 없으면 비어 있다. */
    public Optional<String> firstText() {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.getFirst().text();
    }

    public record Candidate(Content content) {

        /** 긴 출력은 여러 조각으로 올 수 있어 이어 붙인다. thinking 조각은 답변이 아니라서 뺀다. */
        private Optional<String> text() {
            if (content == null || content.parts() == null) {
                return Optional.empty();
            }
            String text = content.parts().stream()
                    .filter(Part::isAnswer)
                    .map(Part::text)
                    .collect(Collectors.joining());
            if (text.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(text);
        }
    }

    public record Content(List<Part> parts) {
    }

    public record Part(String text, Boolean thought) {

        private boolean isAnswer() {
            return text != null && !Boolean.TRUE.equals(thought);
        }
    }
}
