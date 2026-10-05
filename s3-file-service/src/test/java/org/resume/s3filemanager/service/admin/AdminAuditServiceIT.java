package org.resume.s3filemanager.service.admin;

import com.github.javafaker.Faker;
import io.hypersistence.utils.hibernate.type.basic.Inet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.resume.s3filemanager.BaseIntegrationTest;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.dto.AuditLogFilter;
import org.resume.s3filemanager.dto.AuditLogResponse;
import org.resume.s3filemanager.entity.AuditLog;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditServiceIT extends BaseIntegrationTest {

    private static final Faker FAKER = new Faker();

    @Autowired
    private AdminAuditService adminAuditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private String username;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        username = FAKER.name().username();
    }

    private void saveLog(String username, AuditOperation operation,
                         CommonResponseStatus status, Instant timestamp) {
        auditLogRepository.save(AuditLog.builder()
                .username(username)
                .ipAddress(new Inet("127.0.0.1"))
                .operation(operation)
                .status(status)
                .timestamp(timestamp)
                .build());
    }

    /**
     * Фильтрация по username — возвращает только записи нужного пользователя.
     */
    @Test
    void shouldReturnLogs_filteredByUsername() {
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, Instant.now());
        saveLog(FAKER.name().username(), AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, Instant.now());

        AuditLogFilter filter = new AuditLogFilter(
                username,
                null,
                null,
                null,
                null);

        Page<AuditLogResponse> result = adminAuditService.getAuditLogs(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).username()).isEqualTo(username);
    }

    /**
     * Фильтрация по operation — возвращает только записи с нужной операцией.
     */
    @Test
    void shouldReturnLogs_filteredByOperation() {
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, Instant.now());
        saveLog(username, AuditOperation.FILE_DELETE, CommonResponseStatus.SUCCESS, Instant.now());

        AuditLogFilter filter = new AuditLogFilter(
                null,
                AuditOperation.FILE_UPLOAD,
                null,
                null,
                null);

        Page<AuditLogResponse> result = adminAuditService.getAuditLogs(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).operation()).isEqualTo(AuditOperation.FILE_UPLOAD);
    }

    /**
     * Фильтрация по статусу — возвращает только записи с нужным статусом.
     */
    @Test
    void shouldReturnLogs_filteredByStatus() {
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, Instant.now());
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.ERROR, Instant.now());

        AuditLogFilter filter = new AuditLogFilter(
                null,
                null,
                CommonResponseStatus.ERROR,
                null,
                null);

        Page<AuditLogResponse> result = adminAuditService.getAuditLogs(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
    }

    /**
     * Фильтрация по временному диапазону — возвращает только записи в периоде.
     */
    @Test
    void shouldReturnLogs_filteredByTimeRange() {
        Instant past = Instant.now().minusSeconds(3600);
        Instant now = Instant.now();

        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, past);
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, now);

        Instant from = past.minusSeconds(60);
        Instant to = past.plusSeconds(60);

        AuditLogFilter filter = new AuditLogFilter(
                null,
                null,
                null,
                from,
                to);

        Page<AuditLogResponse> result = adminAuditService.getAuditLogs(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    /**
     * Пустой фильтр — возвращает все записи.
     */
    @Test
    void shouldReturnAllLogs_whenFilterIsEmpty() {
        saveLog(username, AuditOperation.FILE_UPLOAD, CommonResponseStatus.SUCCESS, Instant.now());
        saveLog(username, AuditOperation.FILE_DELETE, CommonResponseStatus.ERROR, Instant.now());

        AuditLogFilter filter = new AuditLogFilter(
                null,
                null,
                null,
                null,
                null);

        Page<AuditLogResponse> result = adminAuditService.getAuditLogs(filter, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}