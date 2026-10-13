package org.resume.s3filemanager.audit;

import io.hypersistence.utils.hibernate.type.basic.Inet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.entity.AuditLog;
import org.resume.s3filemanager.repository.AuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Слушатель событий аудита.
 * <p>
 * Асинхронно обрабатывает {@link AuditEvent} и сохраняет их в базу данных.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .requestId(event.getRequestId())
                    .username(event.getUsername())
                    .ipAddress(toInet(event.getIpAddress()))
                    .operation(event.getOperation())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .status(event.getStatus())
                    .details(event.getDetails())
                    .timestamp(event.getOccurredAt())
                    .build();

            auditLogRepository.save(auditLog);

            log.info("Audit logged: {} by {} - {}",
                    event.getOperation(), event.getUsername(), event.getStatus());

        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    private Inet toInet(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return null;
        }
        return new Inet(ipAddress);
    }

}
