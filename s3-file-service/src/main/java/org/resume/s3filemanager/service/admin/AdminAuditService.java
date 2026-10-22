package org.resume.s3filemanager.service.admin;

import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.dto.AuditLogFilter;
import org.resume.s3filemanager.dto.AuditLogResponse;
import org.resume.s3filemanager.entity.QAuditLog;
import org.resume.s3filemanager.mapper.AuditLogMapper;
import org.resume.s3filemanager.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.querydsl.QPageRequest;
import org.springframework.stereotype.Service;

/**
 * Сервис для администрирования журнала аудита.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    /**
     * Возвращает записи аудита по фильтру, новые сверху.
     */
    public Page<AuditLogResponse> getAuditLogs(AuditLogFilter filter, Pageable pageable) {
        BooleanBuilder builder = buildFilter(filter);
        Pageable newestFirst = QPageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), QAuditLog.auditLog.timestamp.desc());

        return auditLogRepository.findAll(builder, newestFirst)
                .map(auditLogMapper::toAuditLogResponse);
    }

    private BooleanBuilder buildFilter(AuditLogFilter filter) {
        QAuditLog qAuditLog = QAuditLog.auditLog;
        BooleanBuilder builder = new BooleanBuilder();

        if (filter.username() != null) {
            builder.and(qAuditLog.username.eq(filter.username()));
        }
        if (filter.operation() != null) {
            builder.and(qAuditLog.operation.eq(filter.operation()));
        }
        if (filter.status() != null) {
            builder.and(qAuditLog.status.eq(filter.status()));
        }
        if (filter.from() != null) {
            builder.and(qAuditLog.timestamp.goe(filter.from()));
        }
        if (filter.to() != null) {
            builder.and(qAuditLog.timestamp.loe(filter.to()));
        }

        return builder;
    }
}
