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
    private static final String S3_IMAGE = "adobe/s3mock:5.2.3";

    private static final String POSTGRES_DB = "s3filemanager_test";
    private static final String POSTGRES_USER = "test";
    private static final String POSTGRES_PASSWORD = "test";

    private static final String REDIS_PASSWORD = "test";

    // S3Mock не проверяет ключи, но SDK требует их наличия
    private static final String S3_ACCESS_KEY = "test";
    private static final String S3_SECRET_KEY = "test";
    private static final String S3_BUCKET = "test-bucket";
    private static final String S3_REGION = "ru-central1";
    private static final String S3_REGION_ENV = "COM_ADOBE_TESTING_S3MOCK_STORE_REGION";
    private static final String S3_READINESS_PATH = "/favicon.ico";

    private static final int REDIS_PORT = 6379;
    private static final int S3_PORT = 9090;

    // CONTAINERS
    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;
    static final KafkaContainer KAFKA;
    static final GenericContainer<?> S3;

    static {
        POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(POSTGRES_DB)
                .withUsername(POSTGRES_USER)
                .withPassword(POSTGRES_PASSWORD);

        REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
                .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

        KAFKA = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));

        S3 = new GenericContainer<>(DockerImageName.parse(S3_IMAGE))
                .withExposedPorts(S3_PORT)
                .withEnv(S3_REGION_ENV, S3_REGION)
                .waitingFor(Wait.forHttp(S3_READINESS_PATH).forPort(S3_PORT));

        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
        S3.start();
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
                () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(S3_PORT));
        registry.add("yandex.storage.accessKey", () -> S3_ACCESS_KEY);
        registry.add("yandex.storage.secretKey", () -> S3_SECRET_KEY);
        registry.add("yandex.storage.bucketName", () -> S3_BUCKET);
        registry.add("yandex.storage.region", () -> S3_REGION);
    }
}