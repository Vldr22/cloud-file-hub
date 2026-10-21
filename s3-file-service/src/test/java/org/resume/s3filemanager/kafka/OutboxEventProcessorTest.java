package org.resume.s3filemanager.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.FileUploadEvent;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.OutboxEvent;
import org.resume.s3filemanager.enums.OutboxEventType;
import org.resume.s3filemanager.enums.OutboxStatus;
import org.resume.s3filemanager.exception.KafkaSendException;
import org.resume.s3filemanager.properties.OutboxProperties;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.repository.OutboxRepository;
import org.resume.s3filemanager.service.kafka.FileEventProducer;
import org.resume.s3filemanager.service.kafka.OutboxEventProcessor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    private static final Faker FAKER = new Faker();
    private static final Long FILE_ID = 1L;

    @Mock
    private FileEventProducer fileEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxProperties outboxProperties;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @InjectMocks
    private OutboxEventProcessor outboxEventProcessor;

    private OutboxEvent outboxEvent;
    private FileUploadEvent fileUploadEvent;

    @BeforeEach
    void setUp() {
        outboxEvent = new OutboxEvent(OutboxEventType.FILE_UPLOAD,
                "{\"fileId\":" + FAKER.number().randomNumber() + "}");

        fileUploadEvent = FileUploadEvent.builder()
                .fileId(FAKER.number().randomNumber())
                .userId(FAKER.number().randomNumber())
                .s3Key(FAKER.internet().uuid() + ".pdf")
                .bucketName(FAKER.internet().slug())
                .originalFileName(FAKER.file().fileName(null, null, "pdf", null))
                .build();
    }

    /**
     * Успешная отправка — статус меняется на SENT, sentAt устанавливается.
     */
    @Test
    void shouldMarkAsSent_whenKafkaSendSucceeds() throws JsonProcessingException {
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenReturn(fileUploadEvent);

        outboxEventProcessor.process(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outboxEvent.getSentAt()).isNotNull();
        assertThat(outboxEvent.getSentAt()).isBefore(Instant.now().plusSeconds(1));

        verify(fileEventProducer).sendFileUploadEvent(fileUploadEvent);
        verify(outboxRepository).save(outboxEvent);
    }

    /**
     * Ошибка Kafka — retry count увеличивается, nextRetryAt обновляется.
     */
    @Test
    void shouldIncrementRetryCount_whenKafkaSendFails() throws JsonProcessingException {
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenReturn(fileUploadEvent);
        doThrow(new KafkaSendException(new RuntimeException(), 1L))
                .when(fileEventProducer).sendFileUploadEvent(any());
        when(outboxProperties.getMaxRetryCount()).thenReturn(3);
        when(outboxProperties.getBaseBackoffMinutes()).thenReturn(5L);

        outboxEventProcessor.process(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getNextRetryAt()).isAfter(Instant.now());

        verify(outboxRepository).save(outboxEvent);
    }

    /**
     * Достигнут лимит retry — статус меняется на FAILED, файл получает ERROR.
     */
    @Test
    void shouldMarkAsFailed_whenMaxRetryCountReached() throws JsonProcessingException {
        FileMetadata file = FileMetadata.builder().id(FILE_ID).build();
        outboxEvent.setRetryCount(2);
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenReturn(fileUploadEvent);
        doThrow(new KafkaSendException(new RuntimeException(), FILE_ID))
                .when(fileEventProducer).sendFileUploadEvent(any());
        when(outboxProperties.getMaxRetryCount()).thenReturn(3);
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.of(file));

        outboxEventProcessor.process(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(3);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.ERROR);

        verify(outboxRepository).save(outboxEvent);
    }

    /**
     * Файл удалён до исчерпания retry — событие всё равно помечается FAILED.
     */
    @Test
    void shouldMarkAsFailed_whenFileAlreadyDeleted() throws JsonProcessingException {
        outboxEvent.setRetryCount(2);
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenReturn(fileUploadEvent);
        doThrow(new KafkaSendException(new RuntimeException(), FILE_ID))
                .when(fileEventProducer).sendFileUploadEvent(any());
        when(outboxProperties.getMaxRetryCount()).thenReturn(3);
        when(fileMetadataRepository.findById(FILE_ID)).thenReturn(Optional.empty());

        outboxEventProcessor.process(outboxEvent);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);

        verify(outboxRepository).save(outboxEvent);
    }

    /**
     * Ошибка десериализации — retry count увеличивается.
     */
    @Test
    void shouldIncrementRetryCount_whenDeserializationFails() throws JsonProcessingException {
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenThrow(new JsonProcessingException("error") {
                });
        when(outboxProperties.getMaxRetryCount()).thenReturn(3);
        when(outboxProperties.getBaseBackoffMinutes()).thenReturn(5L);

        outboxEventProcessor.process(outboxEvent);

        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);

        verifyNoInteractions(fileEventProducer);
        verify(outboxRepository).save(outboxEvent);
    }

    /**
     * Exponential backoff — задержка увеличивается с каждым retry.
     */
    @Test
    void shouldApplyExponentialBackoff_whenRetrying() throws JsonProcessingException {
        when(objectMapper.readValue(any(String.class), eq(FileUploadEvent.class)))
                .thenReturn(fileUploadEvent);
        doThrow(new KafkaSendException(new RuntimeException(), 1L))
                .when(fileEventProducer).sendFileUploadEvent(any());
        when(outboxProperties.getMaxRetryCount()).thenReturn(3);
        when(outboxProperties.getBaseBackoffMinutes()).thenReturn(5L);

        outboxEventProcessor.process(outboxEvent);
        Instant nextRetryAfterFirst = outboxEvent.getNextRetryAt();

        assertThat(nextRetryAfterFirst).isAfter(Instant.now().plusSeconds(9 * 60));

        verify(outboxRepository).save(outboxEvent);
    }
}