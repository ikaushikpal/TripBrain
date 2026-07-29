package com.learn.springai.dto.chatMessage;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessagesDTO {
    private String id;
    private String role;
    private String content;
    private Integer sequenceNumber;
    private LocalDateTime messageTimestamp;
}
