package com.learn.springai.advisor;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import com.learn.springai.model.ChatMessage;
import com.learn.springai.model.Conversation;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.service.ConversationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationPersistenceAdvisor implements CallAdvisor {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationService conversationService;

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
        if (chatClientRequest.prompt() != null &&
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
}