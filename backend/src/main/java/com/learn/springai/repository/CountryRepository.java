package com.learn.springai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.learn.springai.model.Country;

@Repository
public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByIso2(String iso2);

    boolean existsByIso2(String iso2);

    Country findTopByNameIgnoreCaseContaining(String normalized);

    @Query("SELECT c FROM Country c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Country> searchByName(@Param("query") String query);

    Optional<Country> findByIso2IgnoreCase(String normalized);
}