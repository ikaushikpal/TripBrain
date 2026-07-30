package com.learn.springai.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableAsync;

import com.learn.springai.advisor.ConversationPersistenceAdvisor;
import com.learn.springai.repository.DbChatMemoryRepository;
import com.learn.springai.advisor.ContentModerationAdvisor;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.learn.springai.tool.CountryTool;
import com.learn.springai.tool.CurrencyTool;
import com.learn.springai.tool.HotelTool;
import com.learn.springai.tool.NewsTool;
import com.learn.springai.tool.PlaceTool;
import com.learn.springai.tool.RestaurantTool;
import com.learn.springai.tool.TransportTool;
import com.learn.springai.tool.TripRequestTool;
import com.learn.springai.tool.VisaTool;
import com.learn.springai.tool.WeatherTool;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableJpaAuditing
@EnableScheduling
public class ClientClientConfig {

        @Value("classpath:/promptTemplates/startPrompt.st")
        private Resource startSystemPrompt;

        @Value("classpath:/promptTemplates/exportPrompt.st")
        private Resource exportSystemPrompt;

        @Value("${spring.ai.openai.chat.options.model:llama-3.1-8b-instant}")
        private String modelName;

        @Bean
        @Primary
        ChatClient.Builder groqChatClientBuilder(OpenAiChatModel openAiChatModel,
                        ContentModerationAdvisor contentModerationAdvisor) {
                ChatOptions chatOptions = ChatOptions.builder()
                                .model(modelName)
                                .temperature(0.7)
                                .build();
                return ChatClient.builder(openAiChatModel)
                                .defaultOptions(chatOptions)
                                .defaultAdvisors(contentModerationAdvisor);
        }

        @Bean
        ChatMemory chatMemory(
                        DbChatMemoryRepository repository,
                        com.learn.springai.repository.ConversationRepository conversationRepository) {
                return new ChatMemory() {
                        @Override
                        public void add(String conversationId,
                                        List<org.springframework.ai.chat.messages.Message> messages) {
                                // No-op: handled by ConversationPersistenceAdvisor saving directly to DB
                        }

                        @Override
                        public List<org.springframework.ai.chat.messages.Message> get(String conversationId) {
                                List<org.springframework.ai.chat.messages.Message> allMessages = repository
                                                .findByConversationId(conversationId);

                                // Fetch the summary if it exists in the database
                                String summary = conversationRepository.findById(conversationId)
                                                .map(com.learn.springai.model.Conversation::getSummary)
                                                .orElse(null);

                                List<org.springframework.ai.chat.messages.Message> messagesToSend;
                                if (summary != null && !summary.isBlank() && allMessages.size() > 4) {
                                        messagesToSend = new java.util.ArrayList<>();

                                        // Prepend the summary as a system message to context
                                        messagesToSend.add(new org.springframework.ai.chat.messages.SystemMessage(
                                                        "Summary of previous conversation: " + summary));

                                        // Add only the last 4 messages (recent exchange)
                                        messagesToSend.addAll(allMessages.subList(allMessages.size() - 4,
                                                        allMessages.size()));
                                } else {
                                        messagesToSend = allMessages;
                                }

                                // Safety: Truncate any extremely large messages to protect LLM context windows
                                // and rate limits
                                List<org.springframework.ai.chat.messages.Message> sanitizedMessages = new java.util.ArrayList<>();
                                for (org.springframework.ai.chat.messages.Message msg : messagesToSend) {
                                        String content = msg.getText();
                                        if (content != null && content.length() > 1000) {
                                                String truncated = content.substring(0, 1000)
                                                                + "\n...[truncated due to size]...";
                                                if (msg instanceof org.springframework.ai.chat.messages.UserMessage) {
                                                        sanitizedMessages.add(
                                                                        new org.springframework.ai.chat.messages.UserMessage(
                                                                                        truncated));
                                                } else if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage) {
                                                        sanitizedMessages.add(
                                                                        new org.springframework.ai.chat.messages.AssistantMessage(
                                                                                        truncated));
                                                } else if (msg instanceof org.springframework.ai.chat.messages.SystemMessage) {
                                                        sanitizedMessages.add(
                                                                        new org.springframework.ai.chat.messages.SystemMessage(
                                                                                        truncated));
                                                } else {
                                                        sanitizedMessages.add(msg);
                                                }
                                        } else {
                                                sanitizedMessages.add(msg);
                                        }
                                }

                                return sanitizedMessages;
                        }

                        @Override
                        public void clear(String conversationId) {
                                repository.deleteByConversationId(conversationId);
                        }
                };
        }

        @Bean("groqChatClient")
        ChatClient groqChatClient(
                        ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ConversationPersistenceAdvisor conversationPersistenceAdvisor,
                        TripRequestTool tripRequestTool,
                        HotelTool hotelTool,
                        PlaceTool placeTool,
                        RestaurantTool restaurantTool,
                        TransportTool transportTool,
                        WeatherTool weatherTool,
                        NewsTool newsTool,
                        CurrencyTool currencyTool,
                        VisaTool visaTool,
                        CountryTool countryTool) {

                return chatClientBuilder.clone()
                                .defaultSystem(startSystemPrompt)
                                .defaultTools(
                                                tripRequestTool, placeTool, restaurantTool,
                                                hotelTool, transportTool, weatherTool,
                                                newsTool, currencyTool, visaTool, countryTool)
                                .defaultAdvisors(List.of(
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                conversationPersistenceAdvisor,
                                                new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean("exportChatClient")
        ChatClient exportChatClient(ChatClient.Builder chatClientBuilder) {

                ChatOptions exportOptions = ChatOptions.builder()
                                // .model("llama-3.1-8b-instant")
                                .model("llama-3.3-70b-versatile")
                                .temperature(0.2) // lower temp → more deterministic JSON
                                .build();

                return chatClientBuilder.clone()
                                .defaultOptions(exportOptions)
                                .defaultSystem("You are a precise travel assistant. Generate clean, highly accurate travel itinerary sections in structured Markdown as requested by the user.")
                                // No memory advisor — stateless, single-shot call
                                // No persistence — we don't want this in chat history
                                // Keep logger only — useful for debugging export issues
                                .defaultAdvisors(List.of(new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean("routerChatClient")
        ChatClient routerChatClient(ChatClient.Builder chatClientBuilder) {
                return chatClientBuilder.clone().build();
        }

        @Bean("itineraryChatClient")
        ChatClient itineraryChatClient(
                        ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ConversationPersistenceAdvisor conversationPersistenceAdvisor,
                        TripRequestTool tripRequestTool,
                        HotelTool hotelTool,
                        PlaceTool placeTool,
                        RestaurantTool restaurantTool,
                        TransportTool transportTool,
                        WeatherTool weatherTool,
                        NewsTool newsTool,
                        CountryTool countryTool) {
                return chatClientBuilder.clone()
                                .defaultSystem(startSystemPrompt)
                                .defaultTools(tripRequestTool, placeTool, restaurantTool, hotelTool, transportTool,
                                                weatherTool, newsTool, countryTool)
                                .defaultAdvisors(List.of(
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                conversationPersistenceAdvisor,
                                                new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean("visaChatClient")
        ChatClient visaChatClient(
                        ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ConversationPersistenceAdvisor conversationPersistenceAdvisor,
                        TripRequestTool tripRequestTool,
                        VisaTool visaTool,
                        CountryTool countryTool) {
                return chatClientBuilder.clone()
                                .defaultSystem(startSystemPrompt)
                                .defaultTools(tripRequestTool, visaTool, countryTool)
                                .defaultAdvisors(List.of(
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                conversationPersistenceAdvisor,
                                                new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean("budgetChatClient")
        ChatClient budgetChatClient(
                        ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ConversationPersistenceAdvisor conversationPersistenceAdvisor,
                        TripRequestTool tripRequestTool,
                        CurrencyTool currencyTool) {
                return chatClientBuilder.clone()
                                .defaultSystem(startSystemPrompt)
                                .defaultTools(tripRequestTool, currencyTool)
                                .defaultAdvisors(List.of(
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                conversationPersistenceAdvisor,
                                                new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean("generalChatClient")
        ChatClient generalChatClient(
                        ChatClient.Builder chatClientBuilder,
                        ChatMemory chatMemory,
                        ConversationPersistenceAdvisor conversationPersistenceAdvisor,
                        TripRequestTool tripRequestTool) {
                return chatClientBuilder.clone()
                                .defaultSystem(startSystemPrompt)
                                .defaultTools(tripRequestTool)
                                .defaultAdvisors(List.of(
                                                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                                                conversationPersistenceAdvisor,
                                                new SimpleLoggerAdvisor()))
                                .build();
        }

        @Bean
        public org.springframework.boot.web.client.RestClientCustomizer restClientCustomizer() {
                return restClientBuilder -> restClientBuilder
                                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        }

        @Bean
        @Primary
        @org.springframework.context.annotation.Conditional(UseLocalEmbeddingCondition.class)
        public org.springframework.ai.embedding.EmbeddingModel primaryEmbeddingModel() {
                return new org.springframework.ai.transformers.TransformersEmbeddingModel();
        }

        public static class UseLocalEmbeddingCondition implements org.springframework.context.annotation.Condition {
                @Override
                public boolean matches(org.springframework.context.annotation.ConditionContext context,
                                org.springframework.core.type.AnnotatedTypeMetadata metadata) {
                        String apiKey = context.getEnvironment().getProperty("spring.ai.google.genai.api-key");
                        String geminiKey = context.getEnvironment().getProperty("GEMINI_KEY");

                        boolean noApiKey = apiKey == null || apiKey.trim().isEmpty() || "XXX".equals(apiKey.trim())
                                        || "${GEMINI_KEY}".equals(apiKey.trim());
                        boolean noGeminiKey = geminiKey == null || geminiKey.trim().isEmpty()
                                        || "XXX".equals(geminiKey.trim());

                        return noApiKey && noGeminiKey;
                }
        }
}