package com.learn.springai.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.ChatMessage;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    Integer countByConversationIdAndDeletedFalse(String conversationId);

    Optional<ChatMessage> findTopByConversationIdOrderBySequenceNumberDesc(String conversationId);

    List<ChatMessage> findByConversationIdAndDeletedFalseOrderBySequenceNumberAsc(String conversationId);

    List<ChatMessage> findTop20ByConversationIdAndDeletedFalseOrderBySequenceNumberDesc(String convId);

    List<String> findDistinctConversationIdsByDeletedFalse();

    List<ChatMessage> findByConversationIdAndDeletedFalseOrderBySequenceNumberDesc(String conversationId, Pageable pageable);

    List<ChatMessage> findByConversationIdAndDeletedFalseAndSequenceNumberLessThanOrderBySequenceNumberDesc(
            String conversationId, Integer sequenceNumber, Pageable pageable);

    @Query("""
                SELECT COALESCE(MAX(c.sequenceNumber), -1)
                FROM ChatMessage c
                WHERE c.conversation.id = :conversationId
                AND c.deleted = false
            """)
    Integer findMaxSequenceByConversationId(String conversationId);

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.conversation.id = :conversationId")
    void deleteByConversationId(String conversationId);
}
