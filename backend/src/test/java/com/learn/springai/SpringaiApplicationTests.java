package com.learn.springai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    // ── Spring Boot Admin client ─────────────────────────────────────────────
    "spring.boot.admin.client.enabled=false",
    "spring.boot.admin.client.auto-registration=false",

    // ── Database: swap PostgreSQL for in-memory H2 ───────────────────────────
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",

    // ── Redis / Cache: disable Redis so caching uses no-op in tests ──────────
    "spring.data.redis.url=redis://localhost:6379",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",

    // ── Vector Store: switch to OpenSearch so QdrantConfig is skipped ────────
    // QdrantGrpcClient.newBuilder() expects a bare hostname, not "http://..."
    // Switching the type prevents the Qdrant beans from being created at all.
    "spring.ai.vectorstore.type=opensearch",
    "spring.ai.vectorstore.opensearch.uris=http://localhost:9200",
    "spring.ai.vectorstore.opensearch.username=",
    "spring.ai.vectorstore.opensearch.password=",
    "spring.ai.vectorstore.opensearch.index-name=test-index",

    // ── LLM API keys: placeholder values so beans initialise without error ────
    "spring.ai.google.genai.api-key=test-gemini-key",
    "spring.ai.openai.api-key=test-groq-key",

    // ── Backblaze B2 / AWS S3: placeholder values ────────────────────────────
    "aws.s3.endpoint=https://s3.us-west-004.backblazeb2.com",
    "aws.s3.access-key=test-key",
    "aws.s3.secret-key=test-secret",
    "aws.s3.bucket-name=test-bucket",
    "aws.s3.region=us-west-004",

    // ── Tavily web search ─────────────────────────────────────────────────────
    "tavily.api-key=test-tavily-key",

    // ── JWT secret ───────────────────────────────────────────────────────────
    "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",

    // ── Qdrant (kept here as a safety net even though type=opensearch) ────────
    "qdrant.host=localhost",
    "qdrant.api-key=test-key"
})
class SpringaiApplicationTests {

    @Test
    void contextLoads() {
    }

}
