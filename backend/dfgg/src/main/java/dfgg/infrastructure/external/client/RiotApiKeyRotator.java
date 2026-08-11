package dfgg.infrastructure.external.client;

import java.util.List;

final class RiotApiKeyRotator {

    private final List<String> apiKeys;
    private final long[] blockedUntilMillis;

    private int currentIndex;

    RiotApiKeyRotator(List<String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new IllegalArgumentException("apiKeys must not be empty");
        }
        this.apiKeys = List.copyOf(apiKeys);
        this.blockedUntilMillis = new long[apiKeys.size()];
    }

    String currentKey() {
        return apiKeys.get(currentIndex);
    }

    void blockCurrentUntil(long untilMillis) {
        blockedUntilMillis[currentIndex] = Math.max(blockedUntilMillis[currentIndex], untilMillis);
    }

    boolean rotateToNextAvailable(long nowMillis) {
        for (int offset = 1; offset < apiKeys.size(); offset++) {
            int candidate = (currentIndex + offset) % apiKeys.size();
            if (blockedUntilMillis[candidate] <= nowMillis) {
                currentIndex = candidate;
                return true;
            }
        }
        return false;
    }

    long millisUntilEarliestAvailable(long nowMillis) {
        int earliestIndex = 0;
        for (int i = 1; i < blockedUntilMillis.length; i++) {
            if (blockedUntilMillis[i] < blockedUntilMillis[earliestIndex]) {
                earliestIndex = i;
            }
        }
        currentIndex = earliestIndex;
        return Math.max(0, blockedUntilMillis[earliestIndex] - nowMillis);
    }
}
