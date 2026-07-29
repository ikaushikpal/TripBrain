package com.learn.springai.advisor;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import reactor.core.publisher.Flux;

import com.learn.springai.model.ChatMessage;
import com.learn.springai.model.Conversation;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.service.ConversationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationPersistenceAdvisor implements CallAdvisor, StreamAdvisor {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationService conversationService;
    private final org.springframework.ai.openai.OpenAiChatModel chatModel;

    private static final Logger logger = LoggerFactory.getLogger(ConversationPersistenceAdvisor.class);

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getName() {
        return ConversationPersistenceAdvisor.class.getName();
    }

    private ChatClientResponse doAdvise(
            ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        String conversationId = (String) chatClientRequest.context().get(ChatMemory.CONVERSATION_ID);

        if (conversationId == null) {
            return chatClientResponse;
        }

        Conversation conversation = conversationService.getConversation(conversationId);

        if (conversation.getModelName() == null) {
            String modelName = null;

            if (chatClientRequest.prompt() != null &&
                    chatClientRequest.prompt().getOptions() != null) {

                modelName = chatClientRequest.prompt()
                        .getOptions()
                        .getModel();
            }
            conversation.setModelName(modelName);
        }

        Integer lastSequence = chatMessageRepository.findMaxSequenceByConversationId(conversationId);
        if (lastSequence == null)
            lastSequence = -1;

        String userPrompt = "";
        String originalUserMessage = (String) chatClientRequest.context().get("original_user_message");
        if (originalUserMessage != null && !originalUserMessage.isBlank()) {
            userPrompt = originalUserMessage;
        } else if (chatClientRequest.prompt() != null &&
                chatClientRequest.prompt().getUserMessage() != null) {
            userPrompt = chatClientRequest.prompt().getUserMessage().getText();
        }

        chatMessageRepository.save(
                ChatMessage.builder()
                        .conversation(conversation)
                        .role("USER")
                        .content(userPrompt)
                        .sequenceNumber(++lastSequence)
                        .messageTimestamp(LocalDateTime.now())
                        .deleted(false)
                        .build());
        chatMessageRepository.flush();

        String aiResponse = "";
        if (chatClientResponse != null &&
                chatClientResponse.chatResponse() != null &&
                chatClientResponse.chatResponse().getResult() != null &&
                chatClientResponse.chatResponse().getResult().getOutput() != null) {

            aiResponse = chatClientResponse.chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();
        }

        if (conversation.getTitle() == null) {
            conversation.setTitle(
                    userPrompt.length() > 40 ? userPrompt.substring(0, 40) + "..." : userPrompt);
        }

        Usage usage = null;
        if (chatClientResponse != null &&
                chatClientResponse.chatResponse() != null &&
                chatClientResponse.chatResponse().getMetadata() != null) {

            usage = chatClientResponse.chatResponse().getMetadata().getUsage();
        }

        chatMessageRepository.save(
                ChatMessage.builder()
                        .conversation(conversation)
                        .role("ASSISTANT")
                        .content(aiResponse)
                        .sequenceNumber(++lastSequence)
                        .messageTimestamp(LocalDateTime.now())
                        .promptTokens(usage != null ? usage.getPromptTokens() : null)
                        .completionTokens(usage != null ? usage.getTotalTokens() : null)
                        .deleted(false)
                        .build());

        chatMessageRepository.flush();
        conversation.setLastUpdated(LocalDateTime.now());
        conversationService.updateConversation(conversation);

        // Async background conversation summarizer to cap prompt sizes
        final String finalConversationId = conversationId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<ChatMessage> allDbMessages = chatMessageRepository
                        .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(finalConversationId);
                
                // Summarize when we have >= 6 messages, and then update every 4 messages (e.g. 6, 10, 14, 18...)
                if (allDbMessages.size() >= 6 && (allDbMessages.size() - 6) % 4 == 0) {
                    logger.info("Triggering background conversation summarization for conversation ID: {}", finalConversationId);
                    
                    // Summarize all messages EXCEPT the last 4 messages (which remain in windowed active memory)
                    List<ChatMessage> messagesToSummarize = allDbMessages.subList(0, allDbMessages.size() - 4);
                    
                    StringBuilder summaryPrompt = new StringBuilder();
                    summaryPrompt.append("Summarize the following travel planning conversation history between User and Assistant. ")
                            .append("Focus only on established travel choices, preferences, dates, budget details, and destination plans. ")
                            .append("Keep the summary brief and under 2-3 sentences. Do not use markdown.\n\n");
                    
                    for (ChatMessage msg : messagesToSummarize) {
                        summaryPrompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                    }
                    
                    String summary = chatModel.call(summaryPrompt.toString());
                    if (summary != null && !summary.isBlank()) {
                        Conversation conv = conversationService.getConversation(finalConversationId);
                        conv.setSummary(summary.trim());
                        conversationService.updateConversation(conv);
                        logger.info("Successfully updated conversation summary for ID: {}", finalConversationId);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to generate background conversation summary", e);
            }
        });

        return chatClientResponse;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {

        // ChatClientResponse chatClientResponse =
        // callAdvisorChain.nextCall(chatClientRequest);

        // String conversationId = (String)
        // chatClientRequest.context().get(ChatMemory.CONVERSATION_ID);

        // if (conversationId == null) {
        // return chatClientResponse;
        // }

        // Conversation conversation =
        // conversationService.getConversation(conversationId);

        // if (conversation.getModelName() == null) {
        // String modelName = null;

        // if (chatClientRequest.prompt() != null &&
        // chatClientRequest.prompt().getOptions() != null) {

        // modelName = chatClientRequest.prompt()
        // .getOptions()
        // .getModel();
        // }
        // conversation.setModelName(modelName);
        // }

        // Integer lastSequence =
        // chatMessageRepository.findMaxSequenceByConversationId(conversationId);

        // if (lastSequence == null) {
        // lastSequence = -1;
        // }

        // String userPrompt = chatClientRequest.prompt().getUserMessage().getText();

        // chatMessageRepository.save(
        // ChatMessage.builder()
        // .conversation(conversation)
        // .role("USER")
        // .content(userPrompt)
        // .sequenceNumber(++lastSequence)
        // .messageTimestamp(LocalDateTime.now())
        // .deleted(false)
        // .build());

        // String aiResponse = "";

        // if (chatClientResponse != null &&
        // chatClientResponse.chatResponse() != null &&
        // chatClientResponse.chatResponse().getResult() != null &&
        // chatClientResponse.chatResponse().getResult().getOutput() != null) {

        // aiResponse = chatClientResponse.chatResponse()
        // .getResult()
        // .getOutput()
        // .getText();
        // }

        // if (conversation.getTitle() == null) {
        // conversation.setTitle(
        // userPrompt.length() > 40 ? userPrompt.substring(0, 40) + "..." : userPrompt);
        // }

        // Usage usage = null;

        // if (chatClientResponse != null &&
        // chatClientResponse.chatResponse() != null &&
        // chatClientResponse.chatResponse().getMetadata() != null) {

        // usage = chatClientResponse.chatResponse().getMetadata().getUsage();
        // }
        // chatMessageRepository.save(
        // ChatMessage.builder()
        // .conversation(conversation)
        // .role("ASSISTANT")
        // .content(aiResponse)
        // .sequenceNumber(++lastSequence)
        // .messageTimestamp(LocalDateTime.now())
        // .promptTokens(usage != null ? usage.getPromptTokens() : null)
        // .completionTokens(usage != null ? usage.getTotalTokens() : null)
        // .deleted(false)
        // .build());

        // conversation.setLastUpdated(LocalDateTime.now());
        // conversationService.updateConversation(conversation);

        // return chatClientResponse;
        return this.doAdvise(chatClientRequest, callAdvisorChain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(
            ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {

        String conversationId = (String) chatClientRequest.context().get(ChatMemory.CONVERSATION_ID);

        if (conversationId == null) {
            return streamAdvisorChain.nextStream(chatClientRequest);
        }

        Conversation conversation = conversationService.getConversation(conversationId);

        if (conversation.getModelName() == null) {
            String modelName = null;
            if (chatClientRequest.prompt() != null &&
                    chatClientRequest.prompt().getOptions() != null) {
                modelName = chatClientRequest.prompt().getOptions().getModel();
            }
            conversation.setModelName(modelName);
        }

        Integer lastSequence = chatMessageRepository.findMaxSequenceByConversationId(conversationId);
        if (lastSequence == null) lastSequence = -1;

        String userPrompt = "";
        String originalUserMessage = (String) chatClientRequest.context().get("original_user_message");
        if (originalUserMessage != null && !originalUserMessage.isBlank()) {
            userPrompt = originalUserMessage;
        } else if (chatClientRequest.prompt() != null &&
                chatClientRequest.prompt().getUserMessage() != null) {
            userPrompt = chatClientRequest.prompt().getUserMessage().getText();
        }

        chatMessageRepository.save(
                ChatMessage.builder()
                        .conversation(conversation)
                        .role("USER")
                        .content(userPrompt)
                        .sequenceNumber(++lastSequence)
                        .messageTimestamp(LocalDateTime.now())
                        .deleted(false)
                        .build());
        chatMessageRepository.flush();

        final Integer finalLastSequence = lastSequence;
        final String finalUserPrompt = userPrompt;

        Flux<ChatClientResponse> responses = streamAdvisorChain.nextStream(chatClientRequest);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(responses, aggregatedResponse -> {
            String aiResponse = "";
            if (aggregatedResponse != null &&
                    aggregatedResponse.chatResponse() != null &&
                    aggregatedResponse.chatResponse().getResult() != null &&
                    aggregatedResponse.chatResponse().getResult().getOutput() != null) {

                aiResponse = aggregatedResponse.chatResponse()
                        .getResult()
                        .getOutput()
                        .getText();
            }

            if (conversation.getTitle() == null) {
                conversation.setTitle(
                        finalUserPrompt.length() > 40 ? finalUserPrompt.substring(0, 40) + "..." : finalUserPrompt);
            }

            Usage usage = null;
            if (aggregatedResponse != null &&
                    aggregatedResponse.chatResponse() != null &&
                    aggregatedResponse.chatResponse().getMetadata() != null) {

                usage = aggregatedResponse.chatResponse().getMetadata().getUsage();
            }

            chatMessageRepository.save(
                    ChatMessage.builder()
                            .conversation(conversation)
                            .role("ASSISTANT")
                            .content(aiResponse)
                            .sequenceNumber(finalLastSequence + 1)
                            .messageTimestamp(LocalDateTime.now())
                            .promptTokens(usage != null ? usage.getPromptTokens() : null)
                            .completionTokens(usage != null ? usage.getTotalTokens() : null)
                            .deleted(false)
                            .build());

            chatMessageRepository.flush();
            conversation.setLastUpdated(LocalDateTime.now());
            conversationService.updateConversation(conversation);

            // Async background conversation summarizer to cap prompt sizes
            final String finalConversationId = conversationId;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    List<ChatMessage> allDbMessages = chatMessageRepository
                            .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(finalConversationId);
                    
                    // Summarize when we have >= 6 messages, and then update every 4 messages (e.g. 6, 10, 14, 18...)
                    if (allDbMessages.size() >= 6 && (allDbMessages.size() - 6) % 4 == 0) {
                        logger.info("Triggering background conversation summarization for conversation ID: {}", finalConversationId);
                        
                        // Summarize all messages EXCEPT the last 4 messages (which remain in windowed active memory)
                        List<ChatMessage> messagesToSummarize = allDbMessages.subList(0, allDbMessages.size() - 4);
                        
                        StringBuilder summaryPrompt = new StringBuilder();
                        summaryPrompt.append("Summarize the following travel planning conversation history between User and Assistant. ")
                                .append("Focus only on established travel choices, preferences, dates, budget details, and destination plans. ")
                                .append("Keep the summary brief and under 2-3 sentences. Do not use markdown.\n\n");
                        
                        for (ChatMessage msg : messagesToSummarize) {
                            summaryPrompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                        }
                        
                        String summary = chatModel.call(summaryPrompt.toString());
                        if (summary != null && !summary.isBlank()) {
                            Conversation conv = conversationService.getConversation(finalConversationId);
                            conv.setSummary(summary.trim());
                            conversationService.updateConversation(conv);
                            logger.info("Successfully updated conversation summary for ID: {}", finalConversationId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to generate background conversation summary", e);
                }
            });
        });
    }
}