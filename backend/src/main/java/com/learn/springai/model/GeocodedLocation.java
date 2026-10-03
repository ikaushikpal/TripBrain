package com.learn.springai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "geocoded_location", indexes = {
        @Index(name = "idx_geocode_search_query", columnList = "search_query", unique = true),
        @Index(name = "idx_geocode_country", columnList = "country"),
        @Index(name = "idx_geocode_country_code", columnList = "country_code")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeocodedLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "search_query", nullable = false, unique = true, length = 255)
    private String searchQuery;

    @Column(name = "display_name", columnDefinition = "TEXT")
    private String displayName;

    @Column(name = "lat", length = 64)
    private String lat;

    @Column(name = "lon", length = 64)
    private String lon;

    @Column(name = "country", length = 128)
    private String country;

    @Column(name = "country_code", length = 16)
    private String countryCode;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
