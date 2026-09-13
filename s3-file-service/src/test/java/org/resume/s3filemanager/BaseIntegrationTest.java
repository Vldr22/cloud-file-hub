package org.resume.s3filemanager;

import org.junit.jupiter.api.AfterEach;
import org.resume.s3filemanager.scheduler.OutboxScheduler;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@SuppressWarnings("resource")
public abstract class BaseIntegrationTest {

    @MockBean
    private OutboxScheduler outboxScheduler;

    // CONSTANTS
    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.6.0";
    private static final String MINIO_IMAGE = "quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z";

    private static final String POSTGRES_DB = "s3filemanager_test";
    private static final String POSTGRES_USER = "test";
    private static final String POSTGRES_PASSWORD = "test";

    private static final String REDIS_PASSWORD = "test";

    private static final String MINIO_ACCESS_KEY = "minioadmin";
    private static final String MINIO_SECRET_KEY = "minioadmin";
    private static final String MINIO_BUCKET = "test-bucket";
    private static final String MINIO_REGION = "ru-central1";

    private static final int REDIS_PORT = 6379;
    private static final int MINIO_PORT = 9000;

    // CONTAINERS
    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;
    static final KafkaContainer KAFKA;
    static final GenericContainer<?> MINIO;

    static {
        POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(POSTGRES_DB)
                .withUsername(POSTGRES_USER)
                .withPassword(POSTGRES_PASSWORD);

        REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
                .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

        KAFKA = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));

        MINIO = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                .withCommand("server /data")
                .withExposedPorts(MINIO_PORT)
                .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(MINIO_PORT));

        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        MINIO.start();
    }

    @AfterEach
    void resetState() {
        // переопределяется в наследниках при необходимости
    }

    // PROPERTIES
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "test");
        registry.add("spring.redis.password", () -> "test");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        registry.add("yandex.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT));
        registry.add("yandex.storage.accessKey", () -> MINIO_ACCESS_KEY);
        registry.add("yandex.storage.secretKey", () -> MINIO_SECRET_KEY);
        registry.add("yandex.storage.bucketName", () -> MINIO_BUCKET);
        registry.add("yandex.storage.region", () -> MINIO_REGION);
    }
}