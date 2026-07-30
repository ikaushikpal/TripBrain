package com.learn.springai.repository;

import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import com.learn.springai.model.ChatMessage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<ChatMessage> messages = chatMessageRepository
                .findTop20ByConversationIdAndDeletedFalseOrderBySequenceNumberDesc(conversationId);

        Collections.reverse(messages);
        return messages.stream()
                .map(msg -> {
                    Message mappedMessage;

                    if ("USER".equals(msg.getRole())) {
                        mappedMessage = new UserMessage(msg.getContent());
                    } else if ("ASSISTANT".equals(msg.getRole())) {
                        mappedMessage = new AssistantMessage(msg.getContent());
                    } else {
                        throw new RuntimeException("Unknown role");
                    }
                    return mappedMessage;
                })
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // NO OP
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // NO OP
    }

    @Override
    public List<String> findConversationIds() {
        return chatMessageRepository.findDistinctConversationIdsByDeletedFalse().stream()
                .toList();
    }
}
