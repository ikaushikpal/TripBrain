package com.learn.springai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    // ── Lazy init: defer ALL bean creation until first use ────────────────────
    // The contextLoads() test body is empty, so no bean is ever requested,
    // meaning no external connection (Qdrant, OpenSearch, Redis, B2, LLM) is
    // attempted. This is the standard pattern for integration smoke tests that
    // just verify the application context can be assembled without errors.
    "spring.main.lazy-initialization=true",

    // ── Spring Boot Admin client ──────────────────────────────────────────────
    "spring.boot.admin.client.enabled=false",
    "spring.boot.admin.client.auto-registration=false",

    // ── Database: swap PostgreSQL for in-memory H2 ────────────────────────────
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",

    // ── Vector Store: keep Qdrant type, use a bare hostname (no http:// prefix)
    // QdrantGrpcClient.newBuilder() expects just a hostname, not a full URL.
    // With lazy-init=true the Qdrant bean is never constructed in this test.
    "spring.ai.vectorstore.type=qdrant",
    "qdrant.host=localhost",
    "qdrant.api-key=test-key",

    // ── Redis ─────────────────────────────────────────────────────────────────
    "spring.data.redis.url=redis://localhost:6379",

    // ── LLM API keys: placeholder values ─────────────────────────────────────
    "spring.ai.google.genai.api-key=test-gemini-key",
    "spring.ai.openai.api-key=test-groq-key",

    // ── Backblaze B2 / AWS S3: placeholder values ─────────────────────────────
    "aws.s3.endpoint=https://s3.us-west-004.backblazeb2.com",
    "aws.s3.access-key=test-key",
    "aws.s3.secret-key=test-secret",
    "aws.s3.bucket-name=test-bucket",
    "aws.s3.region=us-west-004",

    // ── Tavily web search ─────────────────────────────────────────────────────
    "tavily.api-key=test-tavily-key",

    // ── JWT secret ────────────────────────────────────────────────────────────
    "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
})
class SpringaiApplicationTests {

    @Test
    void contextLoads() {
    }

}
