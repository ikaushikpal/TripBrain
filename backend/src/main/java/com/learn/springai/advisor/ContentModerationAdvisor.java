package com.learn.springai.advisor;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

@Component
public class ContentModerationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(ContentModerationAdvisor.class);

    // List of blocked patterns/keywords (harassment, hate speech, inappropriate words)
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("\\b(bomb|exploding|weapon|kill|suicide|slur|hate|explicit|offensive)\\b", Pattern.CASE_INSENSITIVE)
    );

    private void validateMessage(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return;
        }
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(userPrompt).find()) {
                logger.warn("Inappropriate content detected in prompt: '{}'", userPrompt);
                throw new IllegalArgumentException("Inappropriate content detected. Request blocked for safety reasons.");
            }
        }
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (request.prompt() != null && request.prompt().getUserMessage() != null) {
            validateMessage(request.prompt().getUserMessage().getText());
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (request.prompt() != null && request.prompt().getUserMessage() != null) {
            try {
                validateMessage(request.prompt().getUserMessage().getText());
            } catch (Exception e) {
                return Flux.error(e);
            }
        }
        return chain.nextStream(request);
    }

    @Override
    public String getName() {
        return "ContentModerationAdvisor";
    }

    @Override
    public int getOrder() {
        return 0; // Run first to intercept before sending to models or caching
    }
}
