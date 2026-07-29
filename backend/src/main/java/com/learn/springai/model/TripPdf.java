package com.learn.springai.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.*;

@Entity
@Table(name = "trip_pdf", indexes = {
        @Index(name = "idx_pdf_conv", columnList = "conversation_id"),
        @Index(name = "idx_pdf_checksum", columnList = "checksum"),
        @Index(name = "idx_pdf_public_url", columnList = "publicUrl")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPdf {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String filePath; // relative: uploads/abc123.pdf

    @Column(nullable = false)
    private String publicUrl; // /resources/abc123.pdf

    @Column(nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private String destination; // for display in public listing

    private String thumbnailUrl; // optional, for future use

    @Column
    private String tags;

    @Column
    private String checksum;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false, unique = true)
    @JsonBackReference
    private Conversation conversation;

    @PostRemove
    public void onDelete() {
        try {
            if (filePath != null) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(filePath).toAbsolutePath());
            }
        } catch (Exception e) {
            // Log but don't fail transactional thread
        }
    }
}
