package com.learn.springai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_trip_gallery_view")
@Immutable
@Data
public class PublicTripGalleryItem {

    @Id
    private String id;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "public_url")
    private String publicUrl;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    private String destination;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    private String checksum;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "conversation_title")
    private String conversationTitle;

    @Column(name = "user_name")
    private String userName;

    private String tags;
}
