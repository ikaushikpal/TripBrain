package com.learn.springai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseViewManager {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initView() {
        try {
            // Drop legacy objects with this name first to ensure it is created as a materialized view
            try {
                jdbcTemplate.execute("DROP VIEW IF EXISTS public_trip_gallery_view CASCADE;");
            } catch (Exception e) {
                log.warn("Could not drop regular view: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("DROP TABLE IF EXISTS public_trip_gallery_view CASCADE;");
            } catch (Exception e) {
                log.warn("Could not drop table: {}", e.getMessage());
            }
            try {
                jdbcTemplate.execute("DROP MATERIALIZED VIEW IF EXISTS public_trip_gallery_view CASCADE;");
            } catch (Exception e) {
                log.warn("Could not drop materialized view: {}", e.getMessage());
            }

            // Create Materialized View if not exists
            jdbcTemplate.execute("""
                CREATE MATERIALIZED VIEW IF NOT EXISTS public_trip_gallery_view AS
                SELECT 
                    p.id AS id,
                    p.file_path AS file_path,
                    p.public_url AS public_url,
                    p.generated_at AS generated_at,
                    p.destination AS destination,
                    p.thumbnail_url AS thumbnail_url,
                    p.checksum AS checksum,
                    p.tags AS tags,
                    c.id AS conversation_id,
                    c.title AS conversation_title,
                    u.name AS user_name
                FROM trip_pdf p
                JOIN conversation c ON p.conversation_id = c.id
                JOIN users u ON c.user_id = u.id
                WHERE p.is_public = true AND c.deleted = false;
            """);

            // Create unique index on view ID to support concurrent refreshes
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_gallery_view_id ON public_trip_gallery_view(id);");
            log.info("PostgreSQL Materialized View public_trip_gallery_view initialized successfully.");
        } catch (Exception e) {
            log.warn("Failed to initialize materialized view. Database might not be PostgreSQL yet: {}", e.getMessage());
        }
    }

    public void refreshViewAsync() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        java.util.concurrent.CompletableFuture.runAsync(() -> refreshView());
                    }
                }
            );
        } else {
            java.util.concurrent.CompletableFuture.runAsync(this::refreshView);
        }
    }

    @Scheduled(cron = "0 */10 * * * *") // Every 10 minutes
    public void refreshView() {
        try {
            // Attempt to refresh concurrently (non-blocking)
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY public_trip_gallery_view;");
            log.info("Materialized view public_trip_gallery_view refreshed concurrently.");
        } catch (Exception e) {
            log.warn("Failed concurrent refresh, attempting standard refresh: {}", e.getMessage());
            try {
                jdbcTemplate.execute("REFRESH MATERIALIZED VIEW public_trip_gallery_view;");
                log.info("Materialized view public_trip_gallery_view refreshed successfully.");
            } catch (Exception ex) {
                log.error("Failed to refresh materialized view", ex);
            }
        }
    }
}
