package com.learn.springai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class UserRateLimiterService {

    // Scoped request counts per user, expiring 1 minute after write
    private final Cache<String, Integer> requestCounter = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    public boolean isAllowed(String userId) {
        Integer count = requestCounter.get(userId, k -> 0);
        if (count >= 10) { // Limit: max 10 requests per minute per user
            return false;
        }
        requestCounter.put(userId, count + 1);
        return true;
    }
}
