package com.learn.springai.config;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

import com.learn.springai.advisor.ConversationPersistenceAdvisor;
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

// @Configuration
// public class ClientClientConfig {

//     @Value("classpath:/promptTemplates/startPrompt.st")
//     private Resource startSystemPrompt;

//     /*
//      * Explicit Gemini Builder Bean
//      * Prevents Spring confusion between Gemini/Ollama Chat Models
//      */
//     @Bean
//     @Primary
//     ChatClient.Builder geminiChatClientBuilder(
//             GoogleGenAiChatModel geminiChatModel) {
//         ChatOptions chatOptions = ChatOptions.builder()
//                 // .model("gemma-3-27b-it")
//                 .model("gemini-3.1-flash-lite-preview")
//                 .temperature(0.7)
//                 .build();

//         return ChatClient.builder(geminiChatModel).defaultOptions(chatOptions);
//     }

//     // @Bean
//     // ChatClient geminiChatClient(
//     // ChatClient.Builder chatClientBuilder,
//     // ChatMemory chatMemory,
//     // ConversationPersistenceAdvisor conversationPersistenceAdvisor,
//     // RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
//     // TripRequestTool tripRequestTool,
//     // HotelTool hotelTool,
//     // PlaceTool placeTool,
//     // RestaurantTool restaurantTool,
//     // TransportTool transportTool,
//     // WeatherTool weatherTool) {

//     // Advisor loggerAdvisor = new SimpleLoggerAdvisor();

//     // Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
//     // .build();

//     // return chatClientBuilder.clone()
//     // .defaultSystem(systemPromptTemplate)
//     // .defaultTools(
//     // tripRequestTool,
//     // placeTool,
//     // restaurantTool,
//     // hotelTool,
//     // transportTool,
//     // weatherTool)
//     // .defaultAdvisors(
//     // List.of(
//     // retrievalAugmentationAdvisor,
//     // memoryAdvisor,
//     // conversationPersistenceAdvisor,
//     // loggerAdvisor))
//     // .build();
//     // }

//     // @Bean
//     // RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
//     // VectorStore vectorStore,
//     // @Qualifier("geminiChatClientBuilder") ChatClient.Builder chatClientBuilder,
//     // DocumentRetriever documentRetriever) {

//     // return RetrievalAugmentationAdvisor.builder()

//     // // .queryTransformers(
//     // // TranslationQueryTransformer.builder()
//     // // .chatClientBuilder(chatClientBuilder.clone())
//     // // .targetLanguage("en")
//     // // .build()
//     // // )
//     // // .documentRetriever(request -> {
//     // // String conversationId = (String) request.context().get(CONVERSATION_ID);

//     // // System.out.println("\n==== RETRIEVER ====");
//     // // System.out.println("Query: " + request.text());
//     // // System.out.println("ConversationId: " + conversationId);

//     // // var filteredDocs = vectorStore.similaritySearch(
//     // // SearchRequest.builder()
//     // // .query(request.text())
//     // // .topK(5)
//     // // .similarityThreshold(0.4)
//     // // .filterExpression(
//     // // new FilterExpressionBuilder()
//     // // .eq("conversation_id", conversationId)
//     // // .build())
//     // // .build());

//     // // System.out.println("WITH FILTER: " + filteredDocs.size());

//     // // filteredDocs.forEach(doc -> {
//     // // System.out.println("----- DOC -----");
//     // // System.out.println(doc.getText());
//     // // System.out.println(doc.getMetadata());
//     // // });

//     // // return filteredDocs;
//     // // })

//     // // .documentPostProcessors(
//     // // PIIMaskingDocumentPostProcessor.builder()
//     // // .build())
//     // .documentRetriever(documentRetriever)
//     // .build();
//     // }

//     @Bean
//     ChatMemory chatMemory(SqliteChatMemoryRepository repository) {
//         return MessageWindowChatMemory.builder()
//                 .chatMemoryRepository(repository)
//                 .maxMessages(20)
//                 .build();
//     }

//     @Bean
//     public ChatClient geminiChatClient(
//             ChatClient.Builder chatClientBuilder,
//             ChatMemory chatMemory,
//             ConversationPersistenceAdvisor conversationPersistenceAdvisor,

//             // Tools
//             TripRequestTool tripRequestTool,
//             HotelTool hotelTool,
//             PlaceTool placeTool,
//             RestaurantTool restaurantTool,
//             TransportTool transportTool,
//             WeatherTool weatherTool,
//             NewsTool newsTool,
//             CurrencyTool currencyTool,
//             VisaTool visaTool,
//             CountryTool countryTool) {

//         Advisor loggerAdvisor = new SimpleLoggerAdvisor();

//         Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
//                 .build();

//         return chatClientBuilder.clone()
//                 .defaultSystem(startSystemPrompt)
//                 .defaultTools(
//                         tripRequestTool,
//                         placeTool,
//                         restaurantTool,
//                         hotelTool,
//                         transportTool,
//                         weatherTool,
//                         newsTool,
//                         currencyTool,
//                         visaTool,
//                         countryTool)
//                 .defaultAdvisors(
//                         List.of(
//                                 memoryAdvisor,
//                                 conversationPersistenceAdvisor,
//                                 loggerAdvisor))

//                 .build();
//     }
// }

@Configuration
public class ClientClientConfig {

    @Value("classpath:/promptTemplates/startPrompt.st")
    private Resource startSystemPrompt;

    @Value("classpath:/promptTemplates/exportPrompt.st")
    private Resource exportSystemPrompt;

    @Bean
    @Primary
    ChatClient.Builder geminiChatClientBuilder(GoogleGenAiChatModel geminiChatModel) {
        ChatOptions chatOptions = ChatOptions.builder()
                .model("gemini-3.1-flash-lite-preview")
                .temperature(0.7)
                .build();
        return ChatClient.builder(geminiChatModel).defaultOptions(chatOptions);
    }

    @Bean
    ChatMemory chatMemory(SqliteChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean("geminiChatClient")
    ChatClient geminiChatClient(
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
    ChatClient exportChatClient(
            ChatClient.Builder chatClientBuilder,
            // Export uses tools for data fetching — but NO memory, NO advisors
            TripRequestTool tripRequestTool,
            HotelTool hotelTool,
            PlaceTool placeTool,
            RestaurantTool restaurantTool,
            TransportTool transportTool,
            WeatherTool weatherTool,
            CurrencyTool currencyTool,
            VisaTool visaTool,
            CountryTool countryTool) {

        ChatOptions exportOptions = ChatOptions.builder()
                .model("gemini-3.1-flash-lite-preview")
                .temperature(0.2) // lower temp → more deterministic JSON
                .build();

        return chatClientBuilder.clone()
                .defaultOptions(exportOptions)
                .defaultSystem(exportSystemPrompt)
                .defaultTools(tripRequestTool,
                        hotelTool, placeTool, restaurantTool,
                        transportTool, weatherTool,
                        currencyTool, visaTool, countryTool)
                // No memory advisor — stateless, single-shot call
                // No persistence — we don't want this in chat history
                // Keep logger only — useful for debugging export issues
                .defaultAdvisors(List.of(new SimpleLoggerAdvisor()))
                .build();
    }
}