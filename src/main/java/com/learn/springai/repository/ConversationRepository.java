package com.learn.springai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.Conversation;

@Repository
public interface ConversationRepository
        extends JpaRepository<Conversation, String> {
    List<Conversation> findByUserIdOrderByLastUpdatedDesc(String userId);
}
