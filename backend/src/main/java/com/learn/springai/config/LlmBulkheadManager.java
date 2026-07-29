package com.learn.springai.config;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class LlmBulkheadManager {

    // Free Groq tier limits: max 2 concurrent requests
    private final Semaphore groqSemaphore = new Semaphore(2);

    // Free Google GenAI tier limits: max 1 concurrent request
    private final Semaphore googleSemaphore = new Semaphore(1);

    public <T> T executeWithGroq(Supplier<T> action) {
        int maxRetries = 5;
        int attempt = 0;
        while (true) {
            try {
                groqSemaphore.acquire();
                return action.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request execution interrupted by Groq bulkhead", e);
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries || !isRateLimitException(e)) {
                    throw e;
                }
                long sleepMs = getRetryDelayMs(e, attempt);
                System.out.println("Groq rate limit hit (429). Retrying attempt " + attempt + "/" + maxRetries + " after sleeping " + sleepMs + "ms...");
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Rate limit backoff sleep interrupted", ie);
                }
            } finally {
                groqSemaphore.release();
            }
        }
    }

    public <T> T executeWithGoogle(Supplier<T> action) {
        int maxRetries = 5;
        int attempt = 0;
        while (true) {
            try {
                googleSemaphore.acquire();
                return action.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request execution interrupted by Google GenAI bulkhead", e);
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries || !isRateLimitException(e)) {
                    throw e;
                }
                long sleepMs = getRetryDelayMs(e, attempt);
                System.out.println("Google GenAI rate limit hit (429). Retrying attempt " + attempt + "/" + maxRetries + " after sleeping " + sleepMs + "ms...");
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Rate limit backoff sleep interrupted", ie);
                }
            } finally {
                googleSemaphore.release();
            }
        }
    }

    private boolean isRateLimitException(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("rate limit") || msg.toLowerCase().contains("quota exceeded"))) {
            return true;
        }
        return isRateLimitException(t.getCause());
    }

    private long getRetryDelayMs(Throwable t, int attempt) {
        if (t == null) return getExponentialBackoffMs(attempt);
        String msg = t.getMessage();
        if (msg != null) {
            // Regex to match "Please try again in 9.07s" or similar
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("try again in ([0-9.]+)(m?s)").matcher(msg);
            if (matcher.find()) {
                try {
                    double val = Double.parseDouble(matcher.group(1));
                    String unit = matcher.group(2);
                    if ("s".equals(unit)) {
                        return (long) (val * 1000) + 1500; // add 1.5s buffer
                    } else if ("ms".equals(unit)) {
                        return (long) val + 500; // add 500ms buffer
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        long delay = getRetryDelayMs(t.getCause(), attempt);
        if (delay != getExponentialBackoffMs(attempt)) {
            return delay;
        }
        return getExponentialBackoffMs(attempt);
    }

    private long getExponentialBackoffMs(int attempt) {
        return (long) (Math.pow(2, attempt) * 1000) + 2000;
    }

    public <T> Flux<T> executeStreamWithGroq(Supplier<Flux<T>> streamSupplier) {
        return Flux.defer(() -> {
            try {
                groqSemaphore.acquire();
                return streamSupplier.get()
                        .doFinally(signal -> groqSemaphore.release());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Flux.error(new RuntimeException("Stream subscription interrupted by Groq bulkhead", e));
            }
        });
    }

    public <T> Flux<T> executeStreamWithGoogle(Supplier<Flux<T>> streamSupplier) {
        return Flux.defer(() -> {
            try {
                googleSemaphore.acquire();
                return streamSupplier.get()
                        .doFinally(signal -> googleSemaphore.release());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Flux.error(new RuntimeException("Stream subscription interrupted by Google GenAI bulkhead", e));
            }
        });
    }
}
