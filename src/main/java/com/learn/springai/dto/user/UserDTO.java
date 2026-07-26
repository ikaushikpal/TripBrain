package com.learn.springai.dto.user;

import java.util.List;
import java.util.UUID;

import com.google.auto.value.AutoValue.Builder;
import com.learn.springai.dto.conversation.ConversationDTO;
import com.learn.springai.model.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {
    private String id;
    private String email;
    private String name;
    private List<ConversationDTO> conversations;

    public static UserDTO createFromUser(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        if (user.getConversations() != null) {
            dto.conversations = user.getConversations().stream().map(ConversationDTO::createFromConversation).toList();
        }
        return dto;
    }
}
