package com.learn.springai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.TripPdf;

@Repository
public interface TripPdfRepository extends JpaRepository<TripPdf, String> {
    Optional<TripPdf> findByConversationId(String conversationId);

    List<TripPdf> findAllByIsPublicTrueOrderByGeneratedAtDesc();
}