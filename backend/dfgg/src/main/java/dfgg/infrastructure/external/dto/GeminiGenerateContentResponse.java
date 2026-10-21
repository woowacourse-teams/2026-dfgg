package dfgg.infrastructure.external.dto;

import java.util.List;
import java.util.Optional;

public record GeminiGenerateContentResponse(List<Candidate> candidates) {

    /** 첫 후보의 첫 텍스트. 후보가 없거나 안전 필터로 막혀 텍스트가 없으면 비어 있다. */
    public Optional<String> firstText() {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.getFirst().text();
    }

    public record Candidate(Content content) {

        private Optional<String> text() {
            if (content == null || content.parts() == null) {
                return Optional.empty();
            }
            return content.parts().stream()
                    .map(Part::text)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst();
        }
    }

    public record Content(List<Part> parts) {
    }

    public record Part(String text) {
    }
}
