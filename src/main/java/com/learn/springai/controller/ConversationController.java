package com.learn.springai.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.learn.springai.dto.conversation.ConversationDTO;
import com.learn.springai.dto.conversation.NewConversationDTO;
import com.learn.springai.model.Conversation;
import com.learn.springai.service.ConversationService;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import javax.validation.Valid;

import org.apache.http.HttpStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/new")
    public ResponseEntity<ConversationDTO> startNewConversation(
            @Valid @RequestBody NewConversationDTO newConversationDTO) {
        return ResponseEntity.status(HttpStatus.SC_CREATED)
                .body(conversationService.startNewConversation(newConversationDTO.getUserId()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable("conversationId") String conversationId) {
        ConversationDTO conversation = conversationService.getConversationById(conversationId);
        if (conversation != null) {
            return ResponseEntity.ok(conversation);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getConversationMessages(
            @PathVariable("conversationId") String conversationId) {
        Map<String, Object> response = conversationService.getConversationMessages(conversationId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable("conversationId") String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

}