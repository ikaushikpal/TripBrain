package com.learn.springai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.TripRequest;

@Repository
public interface TripRequestRepository extends JpaRepository<TripRequest, String> {

    Optional<TripRequest> findByConversationId(String conversationId);

    boolean existsByConversationId(String conversationId);

    @Query("""
                SELECT t FROM TripRequest t
                JOIN FETCH t.conversation
                WHERE t.conversation.id = :conversationId
            """)
    Optional<TripRequest> findWithConversationByConversationId(
            @Param("conversationId") String conversationId);
}
