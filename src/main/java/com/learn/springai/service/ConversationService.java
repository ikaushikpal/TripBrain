package com.learn.springai.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.learn.springai.dto.conversation.ConversationDTO;
import com.learn.springai.model.ChatMessage;
import com.learn.springai.model.Conversation;
import com.learn.springai.model.User;
import com.learn.springai.repository.ChatMessageRepository;
import com.learn.springai.repository.ConversationRepository;
import com.learn.springai.repository.UserRepository;
import com.learn.springai.repository.VectorDBRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final UserRepository userRepository;
    private final VectorDBRepository vectorDBRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ConversationDTO startNewConversation(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setConversationStart(LocalDateTime.now());
        conversation.setLastUpdated(LocalDateTime.now());

        Conversation newConversation = conversationRepository.save(conversation);
        return ConversationDTO.createFromConversation(newConversation);
    }

    public Conversation updateConversation(Conversation conversation) {
        conversation.setLastUpdated(LocalDateTime.now());
        Conversation savedConv = conversationRepository.save(conversation);
        conversationRepository.flush();
        return savedConv;
    }

    public Conversation getConversation(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation with id:  " + id + " not found"));
    }

    public ConversationDTO getConversationById(String id) {
        Conversation conversation = this.getConversation(id);
        return ConversationDTO.createFromConversation(conversation);
    }

    public Map<String, Object> getConversationMessages(String conversationId) {
        Conversation conversation = this.getConversation(conversationId);

        if (conversation != null) {
            List<ChatMessage> messages = chatMessageRepository
                    .findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(conversationId);

            return Map.of(
                    "conversation", conversation,
                    "messages", messages.stream()
                            .map(msg -> {
                                Map<String, Object> messageMap = new HashMap<>();
                                messageMap.put("role", msg.getRole());
                                messageMap.put("content", msg.getContent());
                                messageMap.put("timestamp", msg.getMessageTimestamp());
                                return messageMap;
                            })
                            .toList());
        }

        throw new RuntimeException("Conversation not found");
    }

    public void deleteConversation(String conversationId) {
        Conversation conversation = this.getConversation(conversationId);

        if (conversation != null) {
            conversation.setDeleted(true);
            conversation.setLastUpdated(LocalDateTime.now());
            conversationRepository.save(conversation);
        } else {
            throw new RuntimeException("Conversation not found");
        }
    }
}
