package com.learn.springai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.Conversation;

@Repository
public interface ConversationRepository
        extends JpaRepository<Conversation, String> {
    List<Conversation> findByUserIdAndDeletedFalseOrderByPinnedDescLastUpdatedDesc(String userId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT c FROM Conversation c
        WHERE c.deleted = false
        AND (c.title IS NULL OR TRIM(c.title) = '' OR LOWER(c.title) = 'new chat' OR LOWER(c.title) = 'temporary chat')
    """)
    List<Conversation> findUninitiatedConversations();
}
