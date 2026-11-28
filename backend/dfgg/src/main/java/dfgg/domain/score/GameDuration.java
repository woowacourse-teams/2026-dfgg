package dfgg.domain.score;

public record GameDuration(int seconds) {

    private static final double SECONDS_PER_MINUTE = 60.0;

    public double minutes() {
        return seconds / SECONDS_PER_MINUTE;
    }

    public boolean isAtLeast(int minimumSeconds) {
        return seconds >= minimumSeconds;
    }
}
