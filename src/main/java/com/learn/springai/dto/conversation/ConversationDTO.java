package com.learn.springai.dto.conversation;

import java.time.LocalDateTime;
import java.util.UUID;

import com.learn.springai.model.Conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationDTO {
    private String id;
    private String userId;
    private String title;
    private LocalDateTime conversationStart;
    private LocalDateTime lastUpdated;
    private String modelName;

    public static ConversationDTO createFromConversation(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setUserId(conversation.getUser().getId());
        dto.setTitle(conversation.getTitle());
        dto.setConversationStart(conversation.getConversationStart());
        dto.setLastUpdated(conversation.getLastUpdated());
        dto.setModelName(conversation.getModelName());
        return dto;
    }
}
