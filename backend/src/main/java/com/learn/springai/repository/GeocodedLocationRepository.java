package com.learn.springai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.GeocodedLocation;

@Repository
public interface GeocodedLocationRepository extends JpaRepository<GeocodedLocation, String> {

    Optional<GeocodedLocation> findBySearchQuery(String searchQuery);

    List<GeocodedLocation> findAllByCountryIgnoreCase(String country);

    List<GeocodedLocation> findAllByCountryCodeIgnoreCase(String countryCode);
}
