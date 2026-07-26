package com.learn.springai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "country")
@Data
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // India

    @Column(nullable = false, unique = true)
    private String iso2; // IN

    private String iso3; // IND

    private String currency; // INR

    private String capital; // New Delhi

    private String flag; // 🇮🇳

    private String timezone; // Asia/Kolkata

    private String region; // Asia

    private String subregion; // Southern Asia
}