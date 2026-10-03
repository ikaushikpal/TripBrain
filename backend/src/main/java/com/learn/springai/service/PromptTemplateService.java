package com.learn.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Loads prompt template from resources/promptTemplates/{name}.st and caches it in memory.
     */
    public String getTemplate(String templateName) {
        String key = templateName.endsWith(".st") ? templateName : templateName + ".st";
        return templateCache.computeIfAbsent(key, this::loadFromClasspath);
    }

    /**
     * Renders a prompt template by replacing {key} placeholders with variable values.
     */
    public String render(String templateName, Map<String, Object> variables) {
        String template = getTemplate(templateName);
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String val = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replace(placeholder, val);
        }
        return rendered;
    }

    private String loadFromClasspath(String filename) {
        String path = "promptTemplates/" + filename;
        log.info("[PromptTemplateService] Loading prompt template from classpath into in-memory cache: {}", path);
        try {
            Resource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("[PromptTemplateService] Failed to load prompt template: {}", path, e);
            throw new RuntimeException("Could not load prompt template: " + path, e);
        }
    }
}
